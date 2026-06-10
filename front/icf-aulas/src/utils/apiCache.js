/**
 * Lightweight in-memory key-value cache with a 5-minute TTL.
 * Reduces redundant API calls within the same browsing session.
 * All entries are lost on page refresh (no persistence).
 */
const cache = new Map();
const TTL = 5 * 60 * 1000; // 5 minutes in milliseconds

/**
 * Returns the cached value for the given key, or null if absent or expired.
 * Expired entries are evicted on access.
 *
 * @param {string} key - Cache key
 * @returns {*} Cached data or null
 */
export function getCached(key) {
  const cached = cache.get(key);
  if (!cached) return null;

  if (Date.now() - cached.ts > TTL) {
    cache.delete(key);
    return null;
  }

  return cached.data;
}

/**
 * Stores a value in the cache under the given key, stamped with the current time.
 *
 * @param {string} key - Cache key
 * @param {*} data - Value to cache
 */
export function setCache(key, data) {
  cache.set(key, { data, ts: Date.now() });
}

/**
 * Removes a single entry from the cache, or clears the entire cache when no key is provided.
 *
 * @param {string} [key] - Cache key to invalidate; omit to flush all entries
 */
export function clearCache(key) {
  if (key) cache.delete(key);
  else cache.clear();
}
