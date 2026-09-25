workspace "FullTank IAM" "Identity and Access Management bounded context" {
    !identifiers hierarchical

    model {
        client = person "Mobile / Web Client" "Consumes the public and protected REST API."
        admin = person "Platform Administrator" "Manages users and directory data."
        smtp = softwareSystem "SMTP Provider" "Delivers password reset messages."
        platform = softwareSystem "FullTank Platform" "B2B fuel marketplace backend." {
            iam = container "IAM Bounded Context" "Identity, access, users and companies" "Spring Boot / DDD" {
                interfaces = component "Interfaces Layer" "REST controllers, resources, assemblers and ACL" "Spring MVC"
                application = component "Application Layer" "Use cases, command/query services and outbound ports" "Java"
                domain = component "Domain Layer" "Aggregates, commands, queries and repository ports" "Java"
                infrastructure = component "Infrastructure Layer" "JPA adapters, JWT, BCrypt and Spring Security" "Spring / JPA"
            }
            database = container "IAM Database" "Users, roles, companies and reset token hashes" "MySQL"
            ordering = container "Ordering" "Fuel requests and orders" "Spring Boot"
            inventory = container "Inventory" "Fuel products and equipment" "Spring Boot"
            fulfillment = container "Fulfillment" "Drivers, vehicles and deliveries" "Spring Boot"
            reporting = container "Reporting" "Analytics queries" "Spring Boot"
        }

        client -> platform.iam.interfaces "Uses HTTPS/JSON"
        admin -> platform.iam.interfaces "Uses protected HTTPS/JSON"
        platform.iam.interfaces -> platform.iam.application "Invokes use cases"
        platform.iam.application -> platform.iam.domain "Coordinates domain behavior"
        platform.iam.infrastructure -> platform.iam.domain "Implements ports"
        platform.iam.infrastructure -> platform.database "Reads/writes"
        platform.iam.interfaces -> platform.iam.infrastructure "Uses authentication boundary"
        platform.iam.interfaces -> smtp "Requests password reset delivery"
        platform.iam.infrastructure -> platform.ordering "Authorizes requests"
        platform.iam.infrastructure -> platform.inventory "Authorizes requests"
        platform.iam.infrastructure -> platform.fulfillment "Authorizes requests"
        platform.iam.infrastructure -> platform.reporting "Authorizes requests"
    }

    views {
        container platform "iam-context" {
            include client
            include admin
            include platform.iam
            include platform.database
            include smtp
            include platform.ordering
            include platform.inventory
            include platform.fulfillment
            include platform.reporting
            autolayout lr
            title "FullTank IAM bounded context"
        }
        component platform.iam "iam-layers" {
            include client
            include platform.iam.interfaces
            include platform.iam.application
            include platform.iam.domain
            include platform.iam.infrastructure
            autolayout lr
            title "IAM tactical layers"
        }
    }
}
