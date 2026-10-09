package dev.eolmae.marketry.domain.stock.entity;

public record IntegratedPeriod(TradingPeriod preMarket, TradingPeriod regularMarket, TradingPeriod afterMarket) {}
