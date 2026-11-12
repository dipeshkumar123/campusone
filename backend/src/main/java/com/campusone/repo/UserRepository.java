package com.campusone.repo;

import com.campusone.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "campus")
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByRoleAndActiveTrueOrderByName(User.Role role);
    long countByRole(User.Role role);
}
