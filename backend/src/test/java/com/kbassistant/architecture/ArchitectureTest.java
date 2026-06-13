package com.kbassistant.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packages = "com.kbassistant",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    /**
     * Domain layer must be framework-free.
     *
     * This is the highest-value rule in this file. Domain classes exist from
     * Phase 1 onward, so this rule always has classes to check.
     */
    @ArchTest
    static final ArchRule domainHasNoSpringDependencies =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.transaction..",
                            "org.hibernate.."
                    )
                    .allowEmptyShould(true)
                    .because("Domain layer must be framework-free — pure Java only");

    /**
     * API layer must not reach into infrastructure directly.
     *
     * allowEmptyShould(true): api package is empty until Phase 3. Once controllers
     * exist this rule actively enforces the boundary.
     */
    @ArchTest
    static final ArchRule apiDoesNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .because("Controllers must go through application services, not directly to infrastructure");

    /**
     * Domain must not depend on any outer layer.
     */
    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..application..",
                            "..api..",
                            "..infrastructure.."
                    )
                    .allowEmptyShould(true)
                    .because("Domain must not depend on any outer layer");

    /**
     * Application layer must not depend on infrastructure or API.
     *
     * allowEmptyShould(true): application package is empty until Phase 3.
     */
    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..infrastructure..",
                            "..api.."
                    )
                    .allowEmptyShould(true)
                    .because("Application services depend on ports (interfaces), not adapter implementations");

    /**
     * Full layered architecture check.
     *
     * withOptionalLayers(true): allows layers to be empty without failing.
     * As phases progress and packages fill in, this rule starts actively
     * checking real cross-layer dependencies.
     */
    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .withOptionalLayers(true)
                    .layer("API").definedBy("..api..")
                    .layer("Application").definedBy("..application..")
                    .layer("Domain").definedBy("..domain..")
                    .layer("Infrastructure").definedBy("..infrastructure..")
                    .whereLayer("API").mayOnlyBeAccessedByLayers("Infrastructure")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("API", "Infrastructure")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "API");
}
