package nl.loc.data.tagging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "loc.tagging.enabled", havingValue = "true")
public class TagMessaging {

    public static final String EXCHANGE = "loc.tagging";
    public static final String QUEUE = "loc.tagging.refresh";
    public static final String ROUTING_KEY = "tagging.refresh";

    static final String DEAD_LETTER_EXCHANGE = "loc.tagging.dlx";
    static final String DEAD_LETTER_QUEUE = "loc.tagging.refresh.dlq";

    @Bean
    DirectExchange taggingExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    DirectExchange taggingDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue taggingQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    Queue taggingDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding taggingBinding(Queue taggingQueue, DirectExchange taggingExchange) {
        return BindingBuilder.bind(taggingQueue).to(taggingExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding taggingDeadLetterBinding(Queue taggingDeadLetterQueue, DirectExchange taggingDeadLetterExchange) {
        return BindingBuilder.bind(taggingDeadLetterQueue).to(taggingDeadLetterExchange).with(ROUTING_KEY);
    }
}
