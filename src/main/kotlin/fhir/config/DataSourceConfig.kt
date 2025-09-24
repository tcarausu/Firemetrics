package fhir.config

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Profile("!test")
@Configuration
class DataSourceConfig {
    @Bean
    fun dataSource(props: DataSourceProperties): DataSource {
        val real = props.initializeDataSourceBuilder().build()
        return ProxyDataSourceBuilder
            .create(real)
            .name("jdbc-timed")
            .asJson()
            .multiline()
            .countQuery()
            .logQueryBySlf4j()
            .build()
    }
}
