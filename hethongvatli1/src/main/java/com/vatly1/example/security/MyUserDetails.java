package com.vatly1.example.security;

import lombok.RequiredArgsConstructor;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.UserStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.vatly1.example.repository.IUserRepository;

@Service
@RequiredArgsConstructor
public class MyUserDetails implements UserDetailsService {

  private final IUserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    final User user = userRepository.findByUsername(username);

    if (user == null) {
      throw new UsernameNotFoundException("User '" + username + "' not found");
    }

    return org.springframework.security.core.userdetails.User
        .withUsername(username)
        .password(user.getPasswordHash())
        .authorities("ROLE_" + user.getRole().name())
        .accountExpired(false)
        .accountLocked(user.getStatus() == UserStatus.LOCKED)
        .credentialsExpired(false)
        .disabled(false)
        .build();
  }
}
