package id.enterpriseapps.architecture;

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
@AnalyzeClasses(packages = "id.enterpriseapps", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule erp_does_not_depend_on_the_core =
            noClasses()
                    .that().resideInAPackage("id.enterpriseapps.erp..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "id.enterpriseapps.auth..",
                            "id.enterpriseapps.config..",
                            "id.enterpriseapps.controller..",
                            "id.enterpriseapps.service..",
                            "id.enterpriseapps.dto..",
                            "id.enterpriseapps.utils..",
                            "id.enterpriseapps.property..")
                    .because("modul ERP harus tetap bisa diekstrak tanpa membawa aplikasi inti");
}
