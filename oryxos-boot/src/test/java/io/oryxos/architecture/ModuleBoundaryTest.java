package io.oryxos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("io.oryxos");

    @Test
    void coreDoesNotDependOnInfrastructureOrChannels() {
        noClasses()
                .that().resideInAPackage("io.oryxos.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.oryxos.storage..",
                        "io.oryxos.web..",
                        "io.oryxos.channel..")
                .check(classes);
    }
}
