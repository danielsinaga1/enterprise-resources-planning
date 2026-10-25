package id.neravoxa.erp.core;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.PostgreSQLSqlAstTranslator;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * Dialek PostgreSQL untuk persistence unit ERP.
 *
 * <p>Hibernate 6 menulis JPQL {@code like} tanpa ESCAPE sebagai {@code like ? escape ''} di
 * PostgreSQL, sehingga pola dari {@link ErpQuery#contains}/{@link ErpQuery#escapeLike}
 * ({@code \%}, {@code \_}, {@code \\}) diperlakukan harfiah dan pencarian berisi {@code _} atau
 * {@code %} (mis. "PT_ABC", kode role "ERP_VIEWER") tidak pernah cocok. Di sini LIKE tanpa ESCAPE
 * memakai escape bawaan PostgreSQL (backslash) — sama dengan SQL native dan perilaku
 * {@code contains} Prisma. LIKE dengan ESCAPE eksplisit tetap ditulis apa adanya.
 */
public class ErpPostgreSQLDialect extends PostgreSQLDialect {

    @Override
    public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
        return new StandardSqlAstTranslatorFactory() {
            @Override
            protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
                    SessionFactoryImplementor sessionFactory, Statement statement) {
                return new ErpSqlAstTranslator<>(sessionFactory, statement);
            }
        };
    }

    static final class ErpSqlAstTranslator<T extends JdbcOperation> extends PostgreSQLSqlAstTranslator<T> {

        ErpSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
            super(sessionFactory, statement);
        }

        @Override
        public void visitLikePredicate(LikePredicate likePredicate) {
            if (likePredicate.getEscapeCharacter() != null) {
                super.visitLikePredicate(likePredicate);
                return;
            }
            likePredicate.getMatchExpression().accept(this);
            if (likePredicate.isNegated()) {
                appendSql(" not");
            }
            if (likePredicate.isCaseSensitive()) {
                appendSql(" like ");
            } else {
                appendSql(WHITESPACE);
                appendSql(getDialect().getCaseInsensitiveLike());
                appendSql(WHITESPACE);
            }
            likePredicate.getPattern().accept(this);
        }
    }
}
