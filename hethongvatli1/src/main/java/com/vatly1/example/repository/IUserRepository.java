package com.vatly1.example.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vatly1.example.entity.User;

public interface IUserRepository extends JpaRepository<User, UUID> {

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  User findByUsername(String username);

  Optional<User> findByEmail(String email);

  void deleteByUsername(String username);
}
