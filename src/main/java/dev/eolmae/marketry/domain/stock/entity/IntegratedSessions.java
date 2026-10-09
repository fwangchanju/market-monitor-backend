package dev.eolmae.marketry.domain.stock.entity;

public record IntegratedSessions(TradingSession preMarket, TradingSession regularMarket, TradingSession afterMarket) {}
