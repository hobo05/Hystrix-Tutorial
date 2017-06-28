package com.sample;


import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.sample.callable.SampleHystrixConcurrencyStrategy;
import com.sample.commands.WeatherCommand;
import com.sample.commands.WeatherNIOCommand;
import com.sample.logging.LoggingHelper;
import com.sample.utils.RequestScopeObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


/**
 * Unit test for simple App.
 */
public class MockWeatherTest {
    private static final Logger logger = LoggerFactory.getLogger(MockWeatherTest.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final String WEATHER_API_PATH = "/data/2.5/weather?zip=94040,us";

    private HystrixRequestContext context = null;


    @BeforeClass
    public static void setup() {
        if(HystrixPlugins.getInstance().getConcurrencyStrategy() == null) {
            HystrixPlugins.getInstance().registerConcurrencyStrategy(
                    (new SampleHystrixConcurrencyStrategy()));
        }
        AbstractConfiguration config = new AbstractConfiguration() {

            private Map<String, Object> map = new HashMap<>();

            @Override
            public boolean containsKey(String arg0) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public Iterator<String> getKeys() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Object getProperty(String arg0) {
                // TODO Auto-generated method stub
                return map.get(arg0);
            }

            @Override
            public boolean isEmpty() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            protected void addPropertyDirect(String arg0, Object arg1) {
                map.put(arg0, arg1);

            }
        };


        DynamicPropertyFactory.initWithConfigurationSource(config);

        config.addProperty("com.intuit.external.weather.host", "localhost");
        config.addProperty("com.intuit.external.weather.port", "8080");
        config.addProperty("hystrix.command.WeatherCommand.circuitBreaker.forceOpen", false);

    }

    @Before
    public void init() {

        context = HystrixRequestContext.initializeContext();

        stubFor(get(urlEqualTo(WEATHER_API_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withBodyFile("weatherResponse.json")));
    }


    @After
    public void tearDown() {

        LoggingHelper.log();

        WireMock.shutdownServer();
        RequestScopeObject.set(null);
        context.shutdown();
    }


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);

    @Test
    public void testRxNetty() throws IOException {
        HttpClient<ByteBuf, ByteBuf> client = new HttpClientBuilder<ByteBuf, ByteBuf>("localhost", 8080)
                .build();
        String response = client.submit(HttpClientRequest.createGet(WEATHER_API_PATH))
                .flatMap(HttpClientResponse::getContent)
                .map(byteBuf -> {
                    // Convert response to string
                    ByteBufInputStream byteBufInputStream = new ByteBufInputStream(byteBuf);
                    return new Scanner(byteBufInputStream, "utf-8").useDelimiter("\\Z").next();
                })
                .toBlocking()
                .single();

        JsonElement weatherApiResponse = gson.fromJson(response, JsonElement.class);
        logger.info("weatherApiResponse={}", gson.toJson(weatherApiResponse));

    }

    @Test
    public void testWeatherCommandSync() {
        final long startTime = System.currentTimeMillis();
        //Get humidity
        WeatherCommand wCommand = new WeatherCommand("94040");
        Map<String, Double> map = wCommand.execute();
        logger.info("Sync Duration = " + (System.currentTimeMillis() - startTime));
        map.forEach((k, v) -> logger.info("Key : " + k + " Value : " + v));

    }

    @Test
    public void testWeatherCommandASync() throws InterruptedException, ExecutionException {
        final long startTime = System.currentTimeMillis();
        //Get humidity
        WeatherCommand wCommand = new WeatherCommand("94040");
        Future<Map<String, Double>> f = wCommand.queue();
        logger.info("Async Duration = " + (System.currentTimeMillis() - startTime));

        Map<String, Double> map = f.get();
        map.forEach((k, v) -> logger.info("Key : " + k + " Value : " + v));

        logger.info("Async Complete Duration = " + (System.currentTimeMillis() - startTime));

    }

    @Test
    public void testWeatherCommandReactive() throws InterruptedException {
        logger.info(Thread.currentThread().getName());
        String tid = UUID.randomUUID().toString();
        logger.info("Tracking id = " + tid);
        RequestScopeObject.set(tid);

        final long startTime = System.currentTimeMillis();

        WeatherCommand wCommand = new WeatherCommand("94040");
        Observable<Map<String, Double>> o = wCommand.observe();
        logger.info("Reactive Duration = " + (System.currentTimeMillis() - startTime));
        CountDownLatch latch = new CountDownLatch(1);

        o.subscribe(new MySubscriber(startTime, latch));

        latch.await();

    }

    @Test
    public void testWeatherCommandNIO() throws InterruptedException {
        String tid = UUID.randomUUID().toString();
        logger.info(Thread.currentThread().getName());
        logger.info("Tracking id = " + tid);
        RequestScopeObject.set(tid);

        final long startTime = System.currentTimeMillis();

        WeatherNIOCommand wCommand = new WeatherNIOCommand("94040");
        Observable<Map<String, Double>> o = wCommand.observe();
        logger.info("NIO Duration = " + (System.currentTimeMillis() - startTime));
        CountDownLatch latch = new CountDownLatch(1);

        //The following code will print the Hystrix Thread Name and it will be the NIO thread in this case (not the caller thread).
        o.subscribe(new MySubscriber(startTime, latch));

        latch.await();
    }

    private static class MySubscriber extends Subscriber<Map<String, Double>> {
        private final long startTime;
        private final CountDownLatch latch;

        public MySubscriber(long startTime, CountDownLatch latch) {
            this.latch = latch;
            this.startTime = startTime;
        }

        @Override
        public void onCompleted() {
            logger.info("Reactive Complete Duration = " + (System.currentTimeMillis() - startTime));
            latch.countDown();
        }

        @Override
        public void onError(Throwable arg0) {
            logger.info("Error Duration = " + (System.currentTimeMillis() - startTime));
            logger.info(arg0.getMessage());
            latch.countDown();

        }

        @Override
        public void onNext(Map<String, Double> arg0) {
            logger.info("OnNext Duration = " + (System.currentTimeMillis() - startTime));
            logger.info(Thread.currentThread().getName());
            arg0.forEach((k, v) -> logger.info("Key : " + k + " Value : " + v));
        }
    }

}
