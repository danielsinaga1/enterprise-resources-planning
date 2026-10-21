package id.enterpriseapps.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the modular-monolith boundaries introduced for the SaaS migration.
 *
 * The whole point of splitting into Gradle modules is that the boundaries
 * stay sharp over time. These rules fail the build the moment someone couples
 * the core to LIMS (or LIMS back into the core), which is exactly what would
 * make a future "extract LIMS into its own microservice" step painful.
 *
 * Dependency direction we enforce:
 *   :lims   ->  :shared            (allowed)
 *   (core)  ->  :shared, :lims     (allowed, but only via Spring wiring — no imports)
 *   :lims   -/->  core (irms)      (FORBIDDEN)
 *   core    -/->  :lims            (FORBIDDEN)
 */
@AnalyzeClasses(packages = "id.enterpriseapps", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    /**
     * Nothing outside the LIMS module may depend on LIMS classes. This keeps
     * LIMS a leaf that can be lifted out into its own service without dragging
     * the core along. (The core wires LIMS beans via component scanning at
     * runtime, not via compile-time imports — so this holds.)
     *
     * <p>SATU pengecualian terdokumentasi: {@code assistant.tools} (KiPlIAI) menyuntik
     * {@code lims.service.*} langsung ke konstruktor tool-nya dan memakai {@code lims.dto.*}
     * sebagai tipe — 54 pelanggaran, seluruhnya dari paket itu. Ini utang arsitektur nyata,
     * bukan kekeliruan aturan: mengekstrak LIMS jadi service terpisah akan memutus tool-tool
     * tersebut. Memperbaikinya butuh antarmuka di {@code shared} beserta pemindahan DTO-nya,
     * jadi digarap terpisah.
     *
     * <p>Pengecualian sengaja dipersempit ke satu paket, BUKAN dengan menonaktifkan aturan
     * ({@code @ArchIgnore}) dan bukan dengan mengecualikan test ini di CI: keduanya akan
     * membuat kopling baru dari paket mana pun lolos tanpa suara. Bentuk sekarang membuat
     * build tetap merah begitu ada yang menambah ketergantungan ke LIMS dari tempat lain,
     * sekaligus membuat gate CI bisa dipakai sebagai syarat deploy.
     *
     * <p>Begitu {@code assistant.tools} dilepas dari LIMS, HAPUS baris pengecualian di bawah.
     */
    @ArchTest
    static final ArchRule lims_is_not_depended_on_by_the_core =
            noClasses()
                    .that().resideOutsideOfPackage("id.enterpriseapps.lims..")
                    .and().resideOutsideOfPackage("id.enterpriseapps.assistant.tools..")
                    .should().dependOnClassesThat().resideInAPackage("id.enterpriseapps.lims..")
                    .because("LIMS must stay independently extractable as a microservice");

    /**
     * LIMS must not reach into IRMS/core domain packages. It may only use its
     * own code, the shared kernel (response / constant / exception / entity /
     * repository), and third-party libraries.
     */
    @ArchTest
    static final ArchRule lims_does_not_depend_on_core_domain =
            noClasses()
                    .that().resideInAPackage("id.enterpriseapps.lims..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "id.enterpriseapps.service..",            // core + platform services
                            "id.enterpriseapps.controller..",         // IRMS controllers
                            "id.enterpriseapps.request..",            // IRMS request DTOs
                            "id.enterpriseapps.integration.weather..", // weather is an IRMS concern
                            "id.enterpriseapps.observability..")      // core metrics
                    .because("LIMS may only depend on the shared kernel, not on IRMS/core");

    /**
     * The shared kernel is the root of the dependency graph: it must not depend
     * on any feature module (neither LIMS nor IRMS-specific packages). The
     * kernel currently lives in these packages (split-package with the core is
     * intentional and fine on the classpath).
     */
    @ArchTest
    static final ArchRule shared_kernel_stays_a_sink =
            classes()
                    .that().haveSimpleName("Response")
                    .or().haveSimpleName("UserRepository")
                    .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                            "id.enterpriseapps.lims..",
                            "id.enterpriseapps.service..",
                            "id.enterpriseapps.controller..")
                    .because("the shared kernel must not depend on feature modules");
}
