package com.calibre.publisher.service;

import com.calibre.publisher.config.HazelcastCache;
import com.calibre.publisher.model.FxCurrencyRate;
import com.calibre.publisher.model.FxCurrencyRateCsvRow;
import com.calibre.publisher.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FxCurrencyRateServiceImpl implements IFxCurrencyRateService {
    @Value("${forex.endpoint.api-token}")
    String apiToken;

    @Value("${forex.endpoint.fmt}")
    String format;

    private RestTemplate restTemplate;
    private HazelcastCache cacheClient;

    @Autowired
    public FxCurrencyRateServiceImpl(RestTemplate restTemplate, HazelcastCache cacheClient) {
        this.restTemplate = restTemplate;
        this.cacheClient = cacheClient;
    }

    @Override
    public FxCurrencyRate getFxRateByCurrencyPair(String currencyPair) {
        log.info("getFxRateByCurrencyPair currencyPair:{}", currencyPair);
        if (StringUtils.isBlank(currencyPair)) return null;

        Map<String, String> parameters = new HashMap<>();
        parameters.put(Constants.FX_RATE_API_QUERY_PARAMETER_CURRENCY_PAIR, currencyPair + Constants.FX_RATE_API_CURRENCY_PAIR_SUFFIX);
        parameters.put(Constants.FX_RATE_API_QUERY_PARAMETER_API_TOKEN, apiToken);
        parameters.put(Constants.FX_RATE_API_QUERY_PARAMETER_FORMAT, format);

        FxCurrencyRate fxCurrencyRate = this.restTemplate.getForObject(Constants.GET_REALTIME_DATA_FOR_CURRENCY_PAIR, FxCurrencyRate.class, parameters);
        log.info("fxCurrencyRate: {}", fxCurrencyRate);
        return fxCurrencyRate;
    }

    @Override
    public void getFxRateListAndSend(String currencyPair, List<FxCurrencyRateCsvRow> toSendList) {
        log.info("getFxRateListAndSend currencyPair:{}, thread id:{}", currencyPair, Thread.currentThread().getId());
        if (StringUtils.isBlank(currencyPair)) return;

        FxCurrencyRate fxCurrencyRate = this.getFxRateByCurrencyPair(currencyPair);

        if (fxCurrencyRate == null) return;

        FxCurrencyRateCsvRow previousFxCurrencyRateCsvRow = cacheClient.getMessage(currencyPair);
        log.info("previousFxCurrencyRateCsvRow: {}", previousFxCurrencyRateCsvRow);

        if (previousFxCurrencyRateCsvRow == null || Double.compare(previousFxCurrencyRateCsvRow.getValue(), fxCurrencyRate.getClose()) != 0) {
            FxCurrencyRateCsvRow fxCurrencyRateCsvRow = FxCurrencyRateCsvRow.builder().forex(currencyPair)
                    .value(fxCurrencyRate.getClose()).build();

            cacheClient.putMessage(currencyPair, fxCurrencyRateCsvRow);
            toSendList.add(fxCurrencyRateCsvRow);

        }
    }
}
