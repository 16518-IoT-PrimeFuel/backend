package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OrganizationCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.UserCommandService;
import com.primefuel.fulltank.platform.iam.application.internal.outboundservices.hashing.HashingService;
import com.primefuel.fulltank.platform.iam.application.internal.outboundservices.tokens.TokenService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.BuyerCompany;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.ProviderCompany;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.SignInCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.SignUpCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.ProviderCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.RoleRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final RoleRepository roleRepository;
    private final BuyerCompanyRepository buyerCompanyRepository;
    private final ProviderCompanyRepository providerCompanyRepository;
    private final OrganizationCommandService organizationCommandService;
    private final MembershipCommandService membershipCommandService;

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService,
            TokenService tokenService, RoleRepository roleRepository,
            BuyerCompanyRepository buyerCompanyRepository,
            ProviderCompanyRepository providerCompanyRepository,
            OrganizationCommandService organizationCommandService,
            MembershipCommandService membershipCommandService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.buyerCompanyRepository = buyerCompanyRepository;
        this.providerCompanyRepository = providerCompanyRepository;
        this.organizationCommandService = organizationCommandService;
        this.membershipCommandService = membershipCommandService;
    }

    @Override
    public Result<ImmutablePair<User, String>, ApplicationError> handle(SignInCommand command) {
        var user = userRepository.findByUsername(command.username());
        if (user.isEmpty()) {
            return Result.failure(ApplicationError.notFound("User", command.username()));
        }
        if (!hashingService.matches(command.password(), user.get().getPassword())) {
            return Result.failure(ApplicationError.validationError("credentials", "Invalid username or password"));
        }
        var token = tokenService.generateToken(user.get().getUsername());
        return Result.success(ImmutablePair.of(user.get(), token));
    }

    @Override
    @Transactional
    public Result<User, ApplicationError> handle(SignUpCommand command) {
        if (command.roles() == null || command.roles().size() != 1) {
            return Result.failure(ApplicationError.validationError(
                    "roles", "Exactly one buyer or provider role is required"));
        }
        var roleName = command.roles().get(0).getName();
        var validAccount = switch (roleName) {
            case ROLE_BUYER -> command.buyerCompany() != null && command.providerCompany() == null;
            case ROLE_PROVIDER -> command.providerCompany() != null && command.buyerCompany() == null;
        };
        if (!validAccount) {
            return Result.failure(ApplicationError.validationError(
                    "account", "The selected role must include exactly one matching business profile"));
        }
        if (roleName == Roles.ROLE_BUYER) {
            var business = command.buyerCompany();
            if (business.ruc() == null || buyerCompanyRepository.existsByRuc(business.ruc())) {
                return Result.failure(ApplicationError.conflict("Buyer company", "RUC already exists or is missing"));
            }
            if (business.contactEmail() == null || !command.username().equalsIgnoreCase(business.contactEmail())) {
                return Result.failure(ApplicationError.validationError(
                        "username", "Username must match the buyer company's contact email"));
            }
        } else if (command.providerCompany().ruc() == null
                || providerCompanyRepository.existsByRuc(command.providerCompany().ruc())) {
            return Result.failure(ApplicationError.conflict("Provider company", "RUC already exists or is missing"));
        }
        if (userRepository.existsByUsername(command.username())) {
            return Result.failure(ApplicationError.conflict("User", "Username already exists"));
        }
        var roles = command.roles().stream()
                .map(role -> roleRepository.findByName(role.getName()))
                .toList();
        if (roles.stream().anyMatch(java.util.Optional::isEmpty)) {
            return Result.failure(ApplicationError.notFound("Role", "one or more role names"));
        }
        var resolvedRoles = roles.stream().map(java.util.Optional::get).toList();

        var organizationType = roleName == Roles.ROLE_BUYER ? OrganizationType.CUSTOMER : OrganizationType.DISTRIBUTOR;
        var organizationName = command.buyerCompany() != null
                ? command.buyerCompany().name() : command.providerCompany().name();
        var organizationRuc = command.buyerCompany() != null
                ? command.buyerCompany().ruc() : command.providerCompany().ruc();

        var organizationResult = organizationCommandService.handle(
                new CreateOrganizationCommand(organizationName, organizationRuc, organizationType));
        if (organizationResult instanceof Result.Failure<?, ?> failure) {
            return Result.failure((ApplicationError) failure.error());
        }
        var organization = organizationResult.toOptional()
                .orElseThrow(() -> new IllegalStateException("Organization onboarding failed unexpectedly"));

        Long companyId = command.buyerCompany() == null ? null
                : buyerCompanyRepository.save(new BuyerCompany(command.buyerCompany())).getId();
        Long providerId = command.providerCompany() == null ? null
                : providerCompanyRepository.save(new ProviderCompany(command.providerCompany())).getId();
        var user = new User(command.username(), hashingService.encode(command.password()),
                resolvedRoles, companyId, providerId);
        var savedUser = userRepository.save(user);

        var membershipResult = membershipCommandService.handle(new GrantMembershipCommand(
                organization.getId(), savedUser.getId(), MembershipRole.OWNER));
        if (membershipResult instanceof Result.Failure<?, ?> failure) {
            throw new IllegalStateException(
                    "Failed to grant owner membership during signup: " + failure.error());
        }
        return Result.success(savedUser);
    }
}
