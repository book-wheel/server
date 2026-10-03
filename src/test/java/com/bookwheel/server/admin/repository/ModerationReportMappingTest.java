package com.bookwheel.server.admin.repository;

import com.bookwheel.server.admin.entity.ModerationReport;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationReportMappingTest {
    @Test
    void unifiedReportsHaveIndependentLifecycleAndRepositoryQueriesAreValid() {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .applySetting("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
            .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(ModerationReport.class)
                .buildMetadata();
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                var table = metadata.getEntityBinding(ModerationReport.class.getName()).getTable();
                assertThat(table.getForeignKeys()).isEmpty();
                assertThat(table.getUniqueKeys().get("uk_moderation_target_reporter").getColumns())
                    .extracting(Column::getName)
                    .containsExactly("target_type", "target_id", "reporter_user_pk");
                assertThat(table.getColumn(new Column("source_report_id")).isNullable()).isTrue();
                assertThat(table.getColumn(new Column("reporter_user_pk")).isNullable()).isTrue();
                assertThat(new JpaRepositoryFactory(session).getRepository(ModerationReportRepository.class))
                    .isNotNull();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
