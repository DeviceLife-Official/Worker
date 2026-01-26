package client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${custom.api.backend-url}") // <http://100.x.x.x:8080>
    private String backendUrl;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(backendUrl)
                .build();
    }
}

