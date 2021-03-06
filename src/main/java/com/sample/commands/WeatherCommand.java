package com.sample.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.exception.HystrixTimeoutException;
import com.sample.utils.RequestScopeObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//import com.netflix.hystrix.exception.HystrixTimeoutException;


@SuppressWarnings("deprecation")
public final class WeatherCommand extends HystrixCommand<Map<String, Double>> {
    private static final Logger logger = LoggerFactory.getLogger(WeatherCommand.class);
    private final static String QUERY_FORMAT = "/data/2.5/weather?zip=%s,us";
    private final String query;
    private final static Gson gson = new Gson();

    public WeatherCommand(String zip) {
        super(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("WeatherGroup"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        //.withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                        .withExecutionTimeoutEnabled(true)
                        .withExecutionTimeoutInMilliseconds(2000)
                ));


        query = String.format(QUERY_FORMAT, zip);
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Override
    protected Map<String, Double> run() throws IOException, HystrixTimeoutException {
        //Print the value in the request context
        logger.info("Request Scope Object = {}", RequestScopeObject.get());


        Map<String, Object> retMap = null;

        // specify the host, protocol, and port
        String host = DynamicPropertyFactory.getInstance()
                .getStringProperty("com.intuit.external.weather.host", "api.openweathermap.org")
                .get();
        Integer port = DynamicPropertyFactory.getInstance()
                .getIntProperty("com.intuit.external.weather.port", 80)
                .get();

        //logger.info(url + ":" + port);
        HttpHost target = new HttpHost(host, port, "http");


        // specify the get request
        HttpGet getRequest = new HttpGet(query);

        try {
            final HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 1);
            HttpClient httpClient = new DefaultHttpClient(httpParams);
            HttpResponse httpResponse = httpClient.execute(target, getRequest);
            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {
                String jsonString = EntityUtils.toString(entity);
                retMap = gson.fromJson(jsonString, new TypeToken<HashMap<String, Object>>() {
                }.getType());
            }

        } catch (ConnectTimeoutException ex) {
            throw new HystrixTimeoutException();
        }

        return (Map<String, Double>) retMap.get("main");


    }


}
