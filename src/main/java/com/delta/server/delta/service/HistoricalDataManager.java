package com.delta.server.delta.service;

import com.delta.server.delta.model.Candle;
import com.delta.server.delta.repo.CandleRepository;
import io.reactivex.rxjava3.core.Completable;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.delta.server.delta.service.SubscriptionService.buildKey;


@Service
public class HistoricalDataManager {
    public static final ZoneId TZ_IST = ZoneId.of("Asia/Kolkata");
    private static final String BASEURL = "https://api.india.delta.exchange/v2";

    private final OkHttpClient client;
    private final CandleRepository candleRepo;

    public HistoricalDataManager(OkHttpClient client, CandleRepository candleRepo) {
        this.client = client;
        this.candleRepo = candleRepo;
    }


    public Completable fetchAndStore(String symbol, String timeframe, Long product_id) {
        long[] range = getDateRange(timeframe);
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASEURL + "/history/candles")).newBuilder().addQueryParameter("resolution", timeframe).addQueryParameter("symbol", symbol).addQueryParameter("start", String.valueOf(range[0])).addQueryParameter("end", String.valueOf(range[1])).build();

        Request req = new Request.Builder().url(url).addHeader("Accept", "application/json").get().build();
        return Completable.create(emitter -> {
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    System.err.println("Request failed: " + e.getMessage());
                    emitter.onError(e);
                    if (!emitter.isDisposed()) {
                        emitter.onError(e);  // नेटवर्क एरर पर Error भेजें
                    }
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful()) {
                            emitter.onError(new IOException("Unexpected code " + response));
                            return;
                        }
                        String json = Objects.requireNonNull(body).string();

                        String key = buildKey(symbol, timeframe);
                        List<Candle> candles = createCandles(json, timeframe, product_id);
                        Collections.reverse(candles);
                        candleRepo.saveSeries(key, candles);
                        if (!emitter.isDisposed()) {
                            emitter.onComplete();  // रेस्पॉन्स सफल होने पर Complete करें
                        }
                    } catch (IOException e) {
                        if (!emitter.isDisposed()) {
                            emitter.onError(e);
                        }
                    }
                }
            });
        });
    }

    private List<Candle> createCandles(String json, String timeframe, Long product_id) {
        List<Candle> candleList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(json);
        if (jsonObject.has("success")) {
            if (jsonObject.getBoolean("success")) {
                JSONArray result = jsonObject.getJSONArray("result");
                Duration times = parseTimeframe(timeframe);

                for (int i = 0; i < result.length(); i++) {
                    JSONObject obj = result.getJSONObject(i);
                    long time = obj.optLong("time", 0L);
                    double open = obj.optDouble("open", 0.0);
                    double high = obj.optDouble("high", 0.0);
                    double low = obj.optDouble("low", 0.0);
                    double close = obj.optDouble("close", 0.0);
                    long volume = obj.getLong("volume");
                    Instant timestamp = Instant.ofEpochSecond(time);
                    ZonedDateTime tsIst = ZonedDateTime.ofInstant(timestamp, TZ_IST);
                    Candle candle = new Candle(times, tsIst.toInstant(), open, high, low, close, volume, product_id);
                    candleList.add(candle);
                }
            }
        }
        return candleList;
    }

    public static Duration parseTimeframe(String tf) {
        if (tf.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(tf.replace("m", "")));
        } else if (tf.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(tf.replace("h", "")));
        } else if (tf.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(tf.replace("d", "")));
        }
        throw new IllegalArgumentException("Unknown timeframe: " + tf);
    }

    private long[] getDateRange(String interval) {
        ZonedDateTime now = ZonedDateTime.now(TZ_IST);
        String key = interval.toLowerCase();
        ZonedDateTime start = switch (key) {
            case "1m" -> now.minusDays(7);
            case "5m", "15m", "30m" -> now.minusDays(60);
            case "1h", "4h" -> now.minusDays(90);
            case "1d" -> now.minusYears(1);
            case "1wk" -> now.minusYears(5);
            default -> now.minusDays(30);
        };
        return new long[]{start.toEpochSecond(), now.toEpochSecond()};
    }


}
