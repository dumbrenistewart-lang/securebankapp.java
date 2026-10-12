package com.securebank.model;

public record User(String username, byte[] salt, int iterations, byte[] passwordHash) { }
