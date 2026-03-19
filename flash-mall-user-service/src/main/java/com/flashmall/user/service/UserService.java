package com.flashmall.user.service;

import com.flashmall.user.entity.User;

public interface UserService {
    User register(User user);
    User login(String username, String password);
}
