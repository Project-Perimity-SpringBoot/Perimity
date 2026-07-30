package com.perimity.user.storage;

/**
 * What a caller gets back after storing something.
 *
 * The KEY is what goes in the database - never a URL. A URL embeds the bucket,
 * the region and often a signature, all of which change when you move
 * environments or rotate anything. A key survives all of that.
 */
public record StoredObject(String key, String contentType, long sizeBytes) { }
