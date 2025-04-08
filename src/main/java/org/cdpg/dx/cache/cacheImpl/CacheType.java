package org.cdpg.dx.cache.cacheImpl;

public enum CacheType {
  REVOKED_CLIENT("revoked_client"),
  CATALOGUE_CACHE("catalogue_cache");

  String cacheName;

  CacheType(String name) {
    this.cacheName = name;
  }


}
