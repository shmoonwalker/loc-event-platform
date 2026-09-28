package nl.loc.data.weather;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues for weather refresh work.
 *
 * The queue carries work; PostgreSQL remembers which work is due. A lost message therefore
 * costs nothing: the slot is still due and the next scan queues it again.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "loc.weather.enabled", havingValue = "true")
public class WeatherMessaging {

    public static final String EXCHANGE = "loc.weather";
    public static final String REFRESH_QUEUE = "loc.weather.refresh";
    public static final String REFRESH_ROUTING_KEY = "weather.refresh";

    static final String DEAD_LETTER_EXCHANGE = "loc.weather.dlx";
    static final String DEAD_LETTER_QUEUE = "loc.weather.refresh.dlq";

    @Bean
    DirectExchange weatherExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    DirectExchange weatherDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue weatherRefreshQueue() {
        return QueueBuilder.durable(REFRESH_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(REFRESH_ROUTING_KEY)
                .build();
    }

    /** Messages that keep failing land here instead of circling the queue forever. */
    @Bean
    Queue weatherRefreshDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding weatherRefreshBinding(Queue weatherRefreshQueue, DirectExchange weatherExchange) {
        return BindingBuilder.bind(weatherRefreshQueue).to(weatherExchange).with(REFRESH_ROUTING_KEY);
    }

    @Bean
    Binding weatherRefreshDeadLetterBinding(Queue weatherRefreshDeadLetterQueue,
                                            DirectExchange weatherDeadLetterExchange) {
        return BindingBuilder.bind(weatherRefreshDeadLetterQueue)
                .to(weatherDeadLetterExchange)
                .with(REFRESH_ROUTING_KEY);
    }
}
