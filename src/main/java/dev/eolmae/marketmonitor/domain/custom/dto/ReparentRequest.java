package dev.eolmae.marketmonitor.domain.custom.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/** parentId가 null이면 최상위(루트)로 이동한다. */
public record ReparentRequest(@JsonAlias("categoryId") Long parentId) {}
