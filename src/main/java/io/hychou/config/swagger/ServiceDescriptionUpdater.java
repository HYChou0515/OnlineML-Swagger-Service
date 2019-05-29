package io.hychou.config.swagger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ServiceDescriptionUpdater {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDescriptionUpdater.class);

    private static final String DEFAULT_SWAGGER_URL="/v2/api-docs";
    private static final String KEY_SWAGGER_URL="swagger_url";

    @Autowired
    private DiscoveryClient discoveryClient;

    private final RestTemplate template;

    public ServiceDescriptionUpdater(){
        this.template = new RestTemplate();
    }

    @Autowired
    private ServiceDefinitionsContext definitionContext;

    @Scheduled(fixedDelayString= "${swagger.config.refreshrate}")
    public void refreshSwaggerConfigurations(){
        logger.info("Starting Service Definition Context refresh");

        discoveryClient.getServices().stream().forEach(serviceId -> {
            logger.info("Attempting service definition refresh for Service : {} ", serviceId);
            List<ServiceInstance> serviceInstances = discoveryClient.getInstances(serviceId);
            if(serviceInstances == null || serviceInstances.isEmpty()){ //Should not be the case kept for failsafe
                logger.info("No instances available for service : {} ",serviceId);
            }else{
                ServiceInstance instance = serviceInstances.get(0);
                String swaggerURL =  getSwaggerURL( instance);

                Optional<Object> jsonData = getSwaggerDefinitionForAPI(serviceId, swaggerURL);

                if(jsonData.isPresent()){
                    String content = getJSON(serviceId, jsonData.get());
                    definitionContext.addServiceDefinition(serviceId, content);
                }else{
                    logger.error("Skipping service id : {} Error : Could not get Swagger definition from API ",serviceId);
                }

                logger.info("Service Definition Context Refreshed at :  {}",LocalDate.now());
            }
        });
    }

    private String getSwaggerURL( ServiceInstance instance){
        String swaggerURL = instance.getMetadata().get(KEY_SWAGGER_URL);
		String uri = instance.getUri().toString().replaceFirst("https://", "http://");
		logger.info("uri :  {}",uri);
        return swaggerURL != null ? uri+swaggerURL : uri+DEFAULT_SWAGGER_URL;
    }

    private Optional<Object> getSwaggerDefinitionForAPI(String serviceName, String url){
        logger.info("Accessing the SwaggerDefinition JSON for Service : {} : URL : {} ", serviceName, url);
        try{
            Object jsonData = template.getForObject(url, Object.class);
            return Optional.of(jsonData);
        }catch(RestClientException ex){
            logger.error("Error while getting service definition for service : {} Error : {} ", serviceName, ex.getMessage());
            return Optional.empty();
        }

    }

    public String getJSON(String serviceId, Object jsonData){
        try {
            return new ObjectMapper().writeValueAsString(jsonData);
        } catch (JsonProcessingException e) {
            logger.error("Error : {} ", e.getMessage());
            return "";
        }
    }
}