CREATE TABLE api_route_metrics (
    route_key varchar(300) NOT NULL,
    version varchar(5) NOT NULL,
    handler varchar(200) NOT NULL,
    request_count bigint NOT NULL,
    last_seen datetime(6) NOT NULL,
    CONSTRAINT pk_api_route_metrics PRIMARY KEY (route_key)
) ENGINE=InnoDB;

CREATE TABLE api_route_callers (
    route_key varchar(300) NOT NULL,
    caller_key varchar(80) NOT NULL,
    CONSTRAINT pk_api_route_callers PRIMARY KEY (route_key, caller_key),
    CONSTRAINT fk_api_route_callers_route FOREIGN KEY (route_key) REFERENCES api_route_metrics (route_key)
) ENGINE=InnoDB;
