package com.project.custom;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Bounded Context boundaries and layering rules (research R-03, R-27; tasks.md T012).
 */
class ArchitectureTest {

    private static final String BASE = "com.project.custom";
    private static final List<String> BOUNDED_CONTEXTS = List.of("catalog", "cart", "order", "payment");
    private static final List<String> LAYERS = List.of("domain", "application", "infrastructure", "api");

    /** Classes of shared.infrastructure that BC infrastructure adapters may use (R-27, T102). */
    private static final Set<String> SHARED_INFRASTRUCTURE_ALLOWED_FOR_BC_INFRASTRUCTURE = Set.of(
            BASE + ".shared.infrastructure.metrics.AfterCommit",
            BASE + ".shared.infrastructure.config.AppProperties");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    // (1) BC A uses nothing of BC B except its root package (facades, public DTO/event records)
    @ParameterizedTest
    @ValueSource(strings = {"catalog", "cart", "order", "payment"})
    void boundedContextUsesOnlyRootPackageOfOtherContexts(String bc) {
        String[] foreignInternals = BOUNDED_CONTEXTS.stream()
                .filter(other -> !other.equals(bc))
                .flatMap(other -> LAYERS.stream().map(layer -> BASE + "." + other + "." + layer + ".."))
                .toArray(String[]::new);

        noClasses().that().resideInAPackage(BASE + "." + bc + "..")
                .should().dependOnClassesThat().resideInAnyPackage(foreignInternals)
                .because("bounded contexts communicate only through their facades (AGENTS.md)")
                .check(classes);
    }

    // (1) shared.infrastructure is reserved for shared and Spring configuration, with two exceptions
    @Test
    void boundedContextsUseSharedInfrastructureOnlyThroughAllowedExceptions() {
        classes().that().resideInAnyPackage(bcPackages())
                .should(useSharedInfrastructureOnlyAsAllowed())
                .check(classes);
    }

    // (1) shared.api is used by a BC only in its api layer (GuestId argument resolution, error codes)
    @Test
    void sharedApiIsUsedOnlyFromApiLayerOfBoundedContexts() {
        noClasses().that().resideInAnyPackage(bcLayerPackages("domain", "application", "infrastructure"))
                .should().dependOnClassesThat().resideInAPackage(BASE + ".shared.api..")
                .check(classes);
    }

    // (2) the domain is framework-free
    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "com.stripe..")
                .check(classes);
    }

    // (3) constructor injection only
    @Test
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class).check(classes);
    }

    // (4) no ApplicationContext injection
    @Test
    void noDependencyOnApplicationContext() {
        noClasses().should().dependOnClassesThat().areAssignableTo(ApplicationContext.class).check(classes);
    }

    // (5) the Stripe SDK lives only behind the payment ports
    @Test
    void stripeSdkOnlyInStripeAdapter() {
        noClasses().that().resideOutsideOfPackage(BASE + ".payment.infrastructure.stripe..")
                .should().dependOnClassesThat().resideInAPackage("com.stripe..")
                .check(classes);
    }

    // (6) application and infrastructure classes are used only within their own BC
    @ParameterizedTest
    @ValueSource(strings = {"catalog", "cart", "order", "payment"})
    void applicationAndInfrastructureAreInternalToTheirContext(String bc) {
        ArchRule rule = classes()
                .that().resideInAnyPackage(
                        BASE + "." + bc + ".application..",
                        BASE + "." + bc + ".infrastructure..")
                .should().onlyHaveDependentClassesThat().resideInAPackage(BASE + "." + bc + "..");
        rule.check(classes);
    }

    // (7) Dependency Inversion: application never depends on infrastructure
    @Test
    void applicationDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
    }

    // (8) metrics library only in the infrastructure layer (R-27)
    @Test
    void micrometerOnlyInInfrastructure() {
        noClasses().that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("io.micrometer..")
                .check(classes);
    }

    private static String[] bcPackages() {
        return BOUNDED_CONTEXTS.stream().map(bc -> BASE + "." + bc + "..").toArray(String[]::new);
    }

    private static String[] bcLayerPackages(String... layers) {
        return BOUNDED_CONTEXTS.stream()
                .flatMap(bc -> List.of(layers).stream().map(layer -> BASE + "." + bc + "." + layer + ".."))
                .toArray(String[]::new);
    }

    private static ArchCondition<JavaClass> useSharedInfrastructureOnlyAsAllowed() {
        return new ArchCondition<>("use shared.infrastructure only as allowed (AfterCommit, AppProperties "
                + "from the BC infrastructure layer)") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                boolean originIsBcInfrastructure = origin.getPackageName().matches(
                        "com\\.project\\.custom\\.[a-z]+\\.infrastructure(\\..*)?");
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getPackageName().startsWith(BASE + ".shared.infrastructure")) {
                        continue;
                    }
                    boolean allowed = originIsBcInfrastructure
                            && SHARED_INFRASTRUCTURE_ALLOWED_FOR_BC_INFRASTRUCTURE.contains(target.getName())
                            && (!target.getName().endsWith("AfterCommit")
                            || origin.getPackageName().contains(".infrastructure.metrics"));
                    if (!allowed) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
