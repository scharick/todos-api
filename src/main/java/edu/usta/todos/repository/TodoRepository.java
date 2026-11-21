package edu.usta.todos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.usta.todos.model.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findAllByOrderByIdAsc();

    List<Todo> findByCompletedOrderByIdAsc(boolean completed);
}
