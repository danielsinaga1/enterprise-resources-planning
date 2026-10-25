package id.neravoxa.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Menjaga batas modular monolith: modul {@code :erp} hanya bergantung pada {@code :shared}
 * (response / constant / exception) dan pustaka pihak ketiga. Aplikasi inti (auth, RBAC, menu)
 * terhubung ke ERP lewat port yang didefinisikan ERP ({@code ErpIdentityPort}) dan wiring Spring,
 * bukan sebaliknya — sehingga ERP tetap bisa diekstrak menjadi service tersendiri.
 *
 * Arah dependensi:
 *   :erp   ->  :shared                 (boleh)
 *   (core) ->  :shared, :erp           (boleh)
 *   :erp   -/->  core                  (DILARANG)
 */
@AnalyzeClasses(packages = "id.neravoxa", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule erp_does_not_depend_on_the_core =
            noClasses()
                    .that().resideInAPackage("id.neravoxa.erp..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "id.neravoxa.auth..",
                            "id.neravoxa.config..",
                            "id.neravoxa.controller..",
                            "id.neravoxa.service..",
                            "id.neravoxa.dto..",
                            "id.neravoxa.utils..",
                            "id.neravoxa.property..")
                    .because("modul ERP harus tetap bisa diekstrak tanpa membawa aplikasi inti");
}
