package com.apollographql.android.cache;

import java.io.IOException;

import javax.annotation.Nonnull;

import okhttp3.Interceptor;
import okhttp3.Response;

public final class HttpCache {
  public static final String CACHE_KEY_HEADER = "APOLLO-CACHE-KEY";
  public static final String CACHE_CONTROL_HEADER = "APOLLO-CACHE-CONTROL";
  public static final String CACHE_SERVED_DATE_HEADER = "APOLLO-SERVED-DATE";

  private final ResponseCacheStore cacheStore;
  private final EvictionStrategy evictionStrategy;

  public HttpCache(@Nonnull ResponseCacheStore cacheStore, @Nonnull EvictionStrategy evictionStrategy) {
    this.cacheStore = cacheStore;
    this.evictionStrategy = evictionStrategy;
  }

  public void clear() {
    try {
      cacheStore.delete();
    } catch (IOException ignore) {
    }
  }

  public void remove(@Nonnull String cacheKey) throws IOException {
    cacheStore.remove(cacheKey);
  }

  public Response read(@Nonnull String cacheKey) throws IOException {
    ResponseCacheRecord cacheRecord = cacheStore.cacheRecord(cacheKey);
    if (cacheRecord == null) {
      return null;
    }

    Response response = new ResponseHeaderRecord(cacheRecord.headerSource()).response();
    return response.newBuilder()
        .body(new CacheResponseBody(cacheRecord, response))
        .build();
  }

  public Interceptor interceptor() {
    return new CacheInterceptor(this);
  }

  boolean isStale(@Nonnull Response response) {
    return evictionStrategy.isStale(response);
  }

  Response cacheProxy(@Nonnull Response response, @Nonnull String cacheKey) throws IOException {
    ResponseCacheRecordEditor cacheRecordEditor = cacheStore.cacheRecordEditor(cacheKey);
    new ResponseHeaderRecord(response).writeTo(cacheRecordEditor);
    return response.newBuilder()
        .body(new ResponseBodyProxy(cacheRecordEditor, response))
        .build();
  }

  public enum CacheControl {
    DEFAULT("default"),
    NETWORK_ONLY("network-only"),
    CACHE_ONLY("cache-only"),
    NETWORK_BEFORE_STALE("network-before-stale");

    public final String httpHeader;

    CacheControl(String httpHeader) {
      this.httpHeader = httpHeader;
    }

    static CacheControl valueOfHttpHeader(String header) {
      for (CacheControl value : values()) {
        if (value.httpHeader.equals(header)) {
          return value;
        }
      }
      return null;
    }
  }
}