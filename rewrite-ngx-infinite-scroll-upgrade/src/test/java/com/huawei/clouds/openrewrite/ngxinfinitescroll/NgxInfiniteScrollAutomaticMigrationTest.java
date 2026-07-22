package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class NgxInfiniteScrollAutomaticMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateInfiniteScrollModuleToStandaloneDirective());
    }

    @Test
    void migratesNgModuleImportsAndExports() {
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [CommonModule, InfiniteScrollModule],
                  exports: [InfiniteScrollModule]
                })
                export class FeedModule {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [CommonModule, InfiniteScrollDirective],
                  exports: [InfiniteScrollDirective]
                })
                export class FeedModule {}
                """,
                spec -> spec.path("src/feed.module.ts")
        ));
    }

    @Test
    void migratesStandaloneComponentAndTestBedScopes() {
        rewriteRun(
                typescript(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        @Component({ standalone: true, imports: [InfiniteScrollModule] })
                        export class FeedComponent {}
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        @Component({ standalone: true, imports: [InfiniteScrollDirective] })
                        export class FeedComponent {}
                        """,
                        spec -> spec.path("src/feed.component.ts")),
                typescript(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        TestBed.configureTestingModule({ imports: [InfiniteScrollModule] });
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        TestBed.configureTestingModule({ imports: [InfiniteScrollDirective] });
                        """,
                        spec -> spec.path("src/feed.component.spec.ts"))
        );
    }

    @Test
    void preservesNamedImportAliasAndOtherSpecifiers() {
        rewriteRun(typescript(
                """
                import {
                  InfiniteScrollModule as ScrollImports,
                  IInfiniteScrollEvent
                } from "ngx-infinite-scroll";
                @NgModule({ imports: [ScrollImports], exports: [ScrollImports] })
                export class FeedModule {}
                """,
                """
                import {
                  InfiniteScrollDirective as ScrollImports,
                  IInfiniteScrollEvent
                } from "ngx-infinite-scroll";
                @NgModule({ imports: [ScrollImports], exports: [ScrollImports] })
                export class FeedModule {}
                """,
                spec -> spec.path("src/feed.module.ts")
        ));
    }

    @Test
    void realMaherNajarShopAtFixedCommit() {
        // MaherNajar/shop@4b199ca08ae3795ef311936ec2c5bc0f6a13b255
        // src/app/components/products/products.module.ts; package.json has ^9.1.0.
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                import { NgModule } from '@angular/core';
                @NgModule({ imports: [CommonModule, InfiniteScrollModule] })
                export class ProductsModule {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                import { NgModule } from '@angular/core';
                @NgModule({ imports: [CommonModule, InfiniteScrollDirective] })
                export class ProductsModule {}
                """,
                spec -> spec.path("src/app/components/products/products.module.ts")
        ));
    }

    @Test
    void realAngular4RedditAtFixedCommit() {
        // punkrocker178/angular4reddit@f20f806e0710deffd5109ae612b3ab9ac6532520
        // src/app/app.module.ts; package.json has ^10.0.1.
        rewriteRun(typescript(
                """
                import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                import { NgModule } from '@angular/core';
                @NgModule({ imports: [InfiniteScrollModule] })
                export class AppModule {}
                """,
                """
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                import { NgModule } from '@angular/core';
                @NgModule({ imports: [InfiniteScrollDirective] })
                export class AppModule {}
                """,
                spec -> spec.path("src/app/app.module.ts")
        ));
    }

    @Test
    void realJHipsterGeneratorAtFixedCommit() {
        // jhipster/generator-jhipster@47567f4dfb08935c7e4f89fd2113618e3e25fc1
        // shared-libs.module.ts.ejs; generated package.json has 13.0.1.
        rewriteRun(typescript(
                """
                import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [InfiniteScrollModule],
                  exports: [InfiniteScrollModule]
                })
                export class SharedLibsModule {}
                """,
                """
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [InfiniteScrollDirective],
                  exports: [InfiniteScrollDirective]
                })
                export class SharedLibsModule {}
                """,
                spec -> spec.path("generated/src/app/shared/shared-libs.module.ts")
        ));
    }

    @Test
    void realCessdaSharedLibrariesAtFixedCommit() {
        // cessda/cessda.cvs.two@60c3bc60a5f87f04c1420aad8b1d066ff2bf8942
        // src/main/webapp/app/shared/shared-libs.module.ts; package.json has 14.0.1.
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
                @NgModule({
                  imports: [CommonModule, InfiniteScrollModule, FontAwesomeModule],
                  exports: [CommonModule, InfiniteScrollModule, FontAwesomeModule]
                })
                export class SharedLibsModule {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
                @NgModule({
                  imports: [CommonModule, InfiniteScrollDirective, FontAwesomeModule],
                  exports: [CommonModule, InfiniteScrollDirective, FontAwesomeModule]
                })
                export class SharedLibsModule {}
                """,
                spec -> spec.path("src/main/webapp/app/shared/shared-libs.module.ts")
        ));
    }

    @Test
    void realCodeCrowApplicationAtFixedCommit() {
        // da7a90-backup/cro-website@4076096174c4398061d8cbbd520020c4f5ec158e
        // src/app/app.module.ts; package.json has ^14.0.1.
        rewriteRun(typescript(
                """
                import { InfiniteScrollModule } from 'ngx-infinite-scroll'
                @NgModule({ imports: [InfiniteScrollModule] })
                export class AppModule {}
                """,
                """
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll'
                @NgModule({ imports: [InfiniteScrollDirective] })
                export class AppModule {}
                """,
                spec -> spec.path("src/app/app.module.ts")
        ));
    }

    @Test
    void realLumeerAngular16SharedModuleAtFixedCommit() {
        // Lumeer/web-ui@956080521ffc5a1ee0fb705d858cfe4ed09788ab
        // src/app/shared/shared.module.ts; package.json has ^16.0.0.
        rewriteRun(typescript(
                """
                import {FormsModule} from '@angular/forms';
                import {InfiniteScrollModule} from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [FormsModule, InfiniteScrollModule],
                  exports: [FormsModule, InfiniteScrollModule]
                })
                export class SharedModule {}
                """,
                """
                import {FormsModule} from '@angular/forms';
                import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
                @NgModule({
                  imports: [FormsModule, InfiniteScrollDirective],
                  exports: [FormsModule, InfiniteScrollDirective]
                })
                export class SharedModule {}
                """,
                spec -> spec.path("src/app/shared/shared.module.ts")
        ));
    }

    @Test
    void leavesAmbiguousAndUnrelatedShapesUntouched() {
        rewriteRun(
                typescript("""
                           import { InfiniteScrollDirective, InfiniteScrollModule } from 'ngx-infinite-scroll';
                           @NgModule({ imports: [InfiniteScrollModule] })
                           export class DualModule {}
                           """, spec -> spec.path("src/dual.ts")),
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           const wrapped = [InfiniteScrollModule];
                           @NgModule({ imports: [...wrapped] })
                           export class DynamicModule {}
                           """, spec -> spec.path("src/dynamic.ts")),
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           const pluginDescriptor = { imports: [InfiniteScrollModule] };
                           """, spec -> spec.path("src/custom-object.ts")),
                typescript("""
                           import { InfiniteScrollModule } from '@company/scroll';
                           @NgModule({ imports: [InfiniteScrollModule] })
                           export class ForeignModule {}
                           """, spec -> spec.path("src/foreign.ts")),
                typescript("""
                           import type { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           type Legacy = InfiniteScrollModule;
                           """, spec -> spec.path("src/type-only.ts")),
                typescript("""
                           export { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           """, spec -> spec.path("src/reexport.ts")),
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           @NgModule({imports:[InfiniteScrollModule]}) export class InstalledModule {}
                           """, spec -> spec.path("node_modules/example/installed.module.ts"))
        );
    }

    @Test
    void leavesNestedMetadataAndLocalNameCollisionsUntouched() {
        rewriteRun(
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           @NgModule({ providers: [{ imports: [InfiniteScrollModule] }] })
                           export class NestedModule {}
                           """, spec -> spec.path("src/nested.ts")),
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           TestBed.configureTestingModule(wrap({ imports: [InfiniteScrollModule] }));
                           """, spec -> spec.path("src/wrapped-testbed.ts")),
                typescript("""
                           import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                           const InfiniteScrollDirective = customDirective();
                           @NgModule({ imports: [InfiniteScrollModule] })
                           export class NameCollisionModule {}
                           """, spec -> spec.path("src/name-collision.ts"))
        );
    }

    @Test
    void isIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), typescript(
                "import { InfiniteScrollModule } from 'ngx-infinite-scroll';\n@NgModule({imports:[InfiniteScrollModule]}) export class A {}\n",
                "import { InfiniteScrollDirective } from 'ngx-infinite-scroll';\n@NgModule({imports:[InfiniteScrollDirective]}) export class A {}\n"
        ));
    }
}
