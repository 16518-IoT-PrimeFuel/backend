package com.primefuel.fulltank.platform.replenishment.application.internal.commandservices;

import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.EvaluateRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecision;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillThresholds;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillEpisodeRepository;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.replenishment.domain.services.RefillPolicyEvaluator;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Runs the low-level policy. It persists episodes and logs the decision it takes; a replenishment request
 * is generated only when the tank opts in ({@code autoGenerateEnabled} plus a configured product/provider),
 * which is how shadow mode is the default.
 */
@Service
public class RefillPolicyCommandServiceImpl implements RefillPolicyCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(RefillPolicyCommandServiceImpl.class);

    private final RefillPolicyRepository policyRepository;
    private final RefillEpisodeRepository episodeRepository;
    private final ReplenishmentRequestRepository requestRepository;
    private final ReplenishmentCommandService replenishmentCommandService;
    private final Clock clock;

    public RefillPolicyCommandServiceImpl(RefillPolicyRepository policyRepository,
                                          RefillEpisodeRepository episodeRepository,
                                          ReplenishmentRequestRepository requestRepository,
                                          ReplenishmentCommandService replenishmentCommandService,
                                          Clock clock) {
        this.policyRepository = policyRepository;
        this.episodeRepository = episodeRepository;
        this.requestRepository = requestRepository;
        this.replenishmentCommandService = replenishmentCommandService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<RefillPolicy, ApplicationError> handle(ConfigureRefillPolicyCommand command) {
        if (command.tankId() == null || command.organizationId() == null) {
            return Result.failure(ApplicationError.validationError(
                    "refillPolicy", "A tank and an organization are required"));
        }
        var existing = policyRepository.findByTankId(command.tankId());
        if (existing.isPresent() && !command.organizationId().equals(existing.get().getOrganizationId())) {
            return Result.failure(ApplicationError.forbidden("The tank belongs to another organization"));
        }
        try {
            RefillPolicy policy;
            if (existing.isPresent()) {
                policy = existing.get();
                policy.reconfigure(command);
            } else {
                policy = new RefillPolicy(command);
            }
            return Result.success(policyRepository.save(policy));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("refillPolicy", exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public Result<RefillDecision, ApplicationError> handle(EvaluateRefillPolicyCommand command) {
        if (command.tankId() == null || command.organizationId() == null
                || command.level() == null || command.capacity() == null) {
            return Result.failure(ApplicationError.validationError(
                    "refillPolicy", "Tank, organization, level and capacity are required"));
        }

        var policy = policyRepository.findByTankId(command.tankId()).orElse(null);
        var thresholds = policy == null ? RefillThresholds.defaults() : policy.thresholds();
        var policyVersion = policy == null ? 0 : policy.getPolicyVersion();
        var targetLevelPercent = policy == null ? 100.0 : policy.getTargetLevelPercent();
        var targetLevel = command.capacity() * targetLevelPercent / 100.0;
        var unit = Unit.fromCode(command.unit()).name();
        var instant = command.evaluatedAt() == null ? clock.instant() : command.evaluatedAt();
        var openEpisode = episodeRepository.findOpenByTankId(command.tankId());
        var hasPendingRequest = requestRepository.findPendingByTankId(command.tankId()).isPresent();

        RefillDecision decision;
        try {
            decision = RefillPolicyEvaluator.evaluate(command.tankId(), command.organizationId(), policyVersion,
                    command.capacity(), command.level(), targetLevel, unit, thresholds,
                    openEpisode, hasPendingRequest, command.episodeKeySeed(), instant);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("refillPolicy", exception.getMessage()));
        }

        applyDecision(decision, openEpisode, command, policy, policyVersion, targetLevel, unit);
        logDecision(decision, policy);
        return Result.success(decision);
    }

    private void applyDecision(RefillDecision decision,
                               Optional<RefillEpisode> openEpisode,
                               EvaluateRefillPolicyCommand command,
                               RefillPolicy policy,
                               int policyVersion,
                               double targetLevel,
                               String unit) {
        switch (decision.type()) {
            case OPEN_EPISODE -> {
                var episode = episodeRepository.saveAndFlush(new RefillEpisode(
                        decision.episodeKey(), command.tankId(), command.organizationId(), policyVersion,
                        decision.levelPercent(), command.level(), targetLevel, unit, decision.evaluatedAt()));
                maybeGenerateRequest(episode, command, policy);
            }
            case REARM_EPISODE -> openEpisode.ifPresent(episode -> {
                episode.rearm(decision.levelPercent(), decision.evaluatedAt());
                episodeRepository.saveAndFlush(episode);
            });
            default -> {
                // NO_ACTION and SUPPRESSED_PENDING_REQUEST change no state.
            }
        }
    }

    /**
     * Creates at most one request per episode. Idempotency comes from the episode key: the episode is the
     * unit of need, so repeated low readings never produce a second request. A failure here rolls the whole
     * transaction back (episode included), so the trigger can be safely retried.
     */
    private void maybeGenerateRequest(RefillEpisode episode,
                                      EvaluateRefillPolicyCommand command,
                                      RefillPolicy policy) {
        if (policy == null || !policy.canGenerateRequests()) {
            LOG.info("refill shadow episode={} wouldCreateRequest={} (no auto-generation configured)",
                    episode.getEpisodeKey(), episode.getRequestedVolume());
            return;
        }
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                command.organizationId(), command.customerAccountId(), command.tankId(),
                policy.getProviderId(), policy.getFuelProductId(), episode.getRequestedVolume(),
                episode.getUnit(), ReplenishmentSource.AUTOMATIC, episode.getEpisodeKey()));
        if (created.isFailure()) {
            throw new IllegalStateException(
                    "The automatic replenishment request could not be created for episode "
                            + episode.getEpisodeKey());
        }
        var request = created.getOrElse(null);
        episode.markRequestEmitted(request.getId());
        episodeRepository.saveAndFlush(episode);
        LOG.info("refill request generated episode={} requestId={}", episode.getEpisodeKey(), request.getId());
    }

    private void logDecision(RefillDecision decision, RefillPolicy policy) {
        var autoGenerate = policy != null && policy.canGenerateRequests();
        switch (decision.type()) {
            case OPEN_EPISODE -> LOG.info(
                    "refill decision=OPEN_EPISODE tank={} levelPercent={} low={} rearm={} key={} requestedVolume={} {} autoGenerate={}",
                    decision.tankId(), decision.levelPercent(), decision.lowLevelPercent(), decision.rearmPercent(),
                    decision.episodeKey(), decision.requestedVolume(), decision.unit(), autoGenerate);
            case REARM_EPISODE -> LOG.info(
                    "refill decision=REARM_EPISODE tank={} levelPercent={} key={}",
                    decision.tankId(), decision.levelPercent(), decision.episodeKey());
            case SUPPRESSED_PENDING_REQUEST -> LOG.info(
                    "refill decision=SUPPRESSED_PENDING_REQUEST tank={} levelPercent={}",
                    decision.tankId(), decision.levelPercent());
            case NO_ACTION -> LOG.debug(
                    "refill decision=NO_ACTION tank={} levelPercent={}",
                    decision.tankId(), decision.levelPercent());
        }
    }
}
