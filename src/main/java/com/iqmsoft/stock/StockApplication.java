package com.iqmsoft.stock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
public class StockApplication implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(StockApplication.class, args);
	}


	public void intFluxWithBackpressure() {
		Flux<Integer> f = Flux.range(1, 10).map(i -> i * 2).delayElements(Duration.ofMillis(100));
		f.subscribe(new Subscriber<Integer>() {
			Subscription subscription;

			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(1);
				this.subscription = subscription;

			}

			@Override
			public void onNext(Integer integer) {
				this.subscription.request(5);
				System.out.println("integer = " + integer);
			}

			@Override
			public void onError(Throwable throwable) {

			}

			@Override
			public void onComplete() {
				System.out.println("Completed");
			}
		});
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

	}
}

@Service
class StockPriceService {

	RestTemplate restTemplate = new RestTemplate();

	double bitcoinPrice() {
		Tickers tickers= restTemplate.getForObject("https://blockchain.info/ticker",Tickers.class);
		return tickers.USD.last;
	}

	Flux<Long> rtLong() {
		return Flux
				.generate(
						() -> 1L,
						(l, sink) -> {
							sink.next(l);
							if (l > 100) sink.complete();
							return l + l;
						});
	}

	Flux<Stock> rtStockPrice(String stockName) {
		return Flux.<Stock,Stock>generate(
				() -> Stock.of(stockName, 12.2),
				(stock, sink) -> {
				double step = ThreadLocalRandom.current().nextInt(10) / 10d;
					if (stock.getPrice() > 14d) {
						sink.complete();
					}
					sink.next(Stock.of(stock.getName(), stock.getPrice() + step));
					return Stock.of(stock.getName(), stock.getPrice() + step);
				});
	}

	Flux<Double> rtBitcoinPrice() {
		return Flux.interval(Duration.ofSeconds(1))
				.map(count -> bitcoinPrice()).distinct();
	}
}

@RestController
@RequestMapping("/rest")
class StockController {

	@Autowired
	StockPriceService stockPriceService;

	@RequestMapping(value = "/greetings",produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<String> hello() {
		return Mono.just("Hello @" + new Date());
	}

	@RequestMapping(value = "/bitcoin",method = RequestMethod.GET,produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<Double> getBitcoinPrice() {
		return stockPriceService.rtBitcoinPrice();
	}

	@RequestMapping(value = "/longer",method = RequestMethod.GET,produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<Long> getLong() {
		return stockPriceService.rtLong().delayElements(Duration.ofSeconds(1));
	}

	@RequestMapping(value = "/stocks/{stock}", method = RequestMethod.GET, produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<Stock> getStockPrice(@PathVariable("stock") String stockName) {
		return stockPriceService.rtStockPrice(stockName).delayElements(Duration.ofMillis(200));
	}
}

@Value
@Getter
@AllArgsConstructor(staticName = "of")
class Stock {
	String name;
	double price;
}

@Data
class Tickers {
	@JsonProperty("USD")
	Ticker USD;

	@Data
	class Ticker {
		double last;
	}
}
