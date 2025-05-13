# LMS Implementation Plan

## Project Structure
```
src/main/java/com/example/demo/
├── config/
│   ├── SecurityConfig.java
│   ├── WebConfig.java
│   └── SQLiteDialect.java
├── controller/
│   ├── AuthController.java
│   ├── CourseController.java
│   ├── LessonController.java
│   ├── ContentController.java
│   ├── ExamController.java
│   ├── ProgressController.java
│   └── ViewController.java (for Thymeleaf views)
├── model/
│   ├── User.java
│   ├── Role.java
│   ├── Course.java
│   ├── Lesson.java
│   ├── Content.java
│   ├── ContentType.java
│   ├── FileMetadata.java
│   ├── Exam.java
│   ├── Question.java
│   ├── Answer.java
│   ├── Submission.java
│   └── Progress.java
├── repository/
│   ├── UserRepository.java
│   ├── CourseRepository.java
│   ├── LessonRepository.java
│   ├── ContentRepository.java
│   ├── FileMetadataRepository.java
│   ├── ExamRepository.java
│   ├── QuestionRepository.java
│   ├── SubmissionRepository.java
│   └── ProgressRepository.java
├── service/
│   ├── UserService.java
│   ├── CourseService.java
│   ├── LessonService.java
│   ├── ContentService.java
│   ├── FileStorageService.java
│   ├── ExamService.java
│   └── ProgressService.java
├── dto/
│   ├── UserDTO.java
│   ├── CourseDTO.java
│   ├── LessonDTO.java
│   └── ... (other DTOs as needed)
└── DemoApplication.java
```

## 1. Update Dependencies (pom.xml)

```xml
<!-- Add these to your existing pom.xml -->
<dependencies>
    <!-- Already included in your pom.xml -->
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Add these new dependencies -->
    <!-- SQLite JDBC Driver -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.42.0.0</version>
    </dependency>
    
    <!-- JPA for database operations -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- Web support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Thymeleaf for views (Hybrid approach) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- For file handling -->
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.13.0</version>
    </dependency>
</dependencies>
```

## 2. Configure Database (application.properties)

```properties
# Existing property
spring.application.name=demo

# Database configuration for SQLite
spring.datasource.url=jdbc:sqlite:lms.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=com.example.demo.config.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update

# Disable Spring Security's default login form (we'll create our own)
spring.security.user.name=disabled
spring.security.user.password=disabled

# File storage
file.upload-dir=./uploads
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB

# Thymeleaf cache settings
spring.thymeleaf.cache=false
```

## 3. SQLite Dialect Configuration

```java
// src/main/java/com/example/demo/config/SQLiteDialect.java
package com.example.demo.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.dialect.function.VarArgsSQLFunction;
import org.hibernate.type.StandardBasicTypes;

import java.sql.Types;

public class SQLiteDialect extends Dialect {

    public SQLiteDialect() {
        registerColumnType(Types.BIT, "integer");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.REAL, "real");
        registerColumnType(Types.DOUBLE, "double");
        registerColumnType(Types.NUMERIC, "numeric");
        registerColumnType(Types.DECIMAL, "decimal");
        registerColumnType(Types.CHAR, "char");
        registerColumnType(Types.VARCHAR, "varchar");
        registerColumnType(Types.LONGVARCHAR, "longvarchar");
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "timestamp");
        registerColumnType(Types.BINARY, "blob");
        registerColumnType(Types.VARBINARY, "blob");
        registerColumnType(Types.LONGVARBINARY, "blob");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.CLOB, "clob");
        registerColumnType(Types.BOOLEAN, "integer");

        registerFunction("concat", new VarArgsSQLFunction(StandardBasicTypes.STRING, "", "||", ""));
        registerFunction("mod", new SQLFunctionTemplate(StandardBasicTypes.INTEGER, "?1 % ?2"));
        registerFunction("substr", new StandardSQLFunction("substr", StandardBasicTypes.STRING));
        registerFunction("substring", new StandardSQLFunction("substr", StandardBasicTypes.STRING));
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new SQLiteIdentityColumnSupport();
    }

    @Override
    public boolean hasAlterTable() {
        return false;
    }

    @Override
    public boolean dropConstraints() {
        return false;
    }

    @Override
    public String getDropForeignKeyString() {
        return "";
    }

    @Override
    public String getAddForeignKeyConstraintString(String constraintName, String[] foreignKey, String referencedTable, String[] primaryKey, boolean referencesPrimaryKey) {
        return "";
    }

    @Override
    public String getAddPrimaryKeyConstraintString(String constraintName) {
        return "";
    }

    @Override
    public String getForUpdateString() {
        return "";
    }

    @Override
    public String getAddColumnString() {
        return "add column";
    }

    @Override
    public boolean supportsOuterJoinForUpdate() {
        return false;
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public boolean supportsCascadeDelete() {
        return false;
    }

    public static class SQLiteIdentityColumnSupport extends IdentityColumnSupportImpl {
        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select last_insert_rowid()";
        }

        @Override
        public String getIdentityColumnString(int type) {
            return "integer";
        }
    }
}
```

## 4. Web Configuration for CORS

```java
// src/main/java/com/example/demo/config/WebConfig.java
package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8080") // For development
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);
    }
}
```

## 5. Basic Security Configuration

```java
// src/main/java/com/example/demo/config/SecurityConfig.java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disabling CSRF for simplicity (student project)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## 6. User and Role Models

```java
// src/main/java/com/example/demo/model/Role.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String name;
}
```

```java
// src/main/java/com/example/demo/model/User.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lms_user") // Avoiding "user" table name which is reserved in some DBs
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    private String fullName;
    
    @Column(unique = true)
    private String email;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
```

## 7. Course and Lesson Models

```java
// src/main/java/com/example/demo/model/Course.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    @ManyToOne
    private User teacher;
    
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "course_enrollments",
        joinColumns = @JoinColumn(name = "course_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> enrolledStudents = new ArrayList<>();
}
```

```java
// src/main/java/com/example/demo/model/Lesson.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    private Integer orderIndex; // For ordering lessons within a course
    
    @ManyToOne
    private Course course;
    
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Content> contents = new ArrayList<>();
    
    @OneToOne(cascade = CascadeType.ALL)
    private Exam exam;
}
```

## 8. Content Models and File Handling

```java
// src/main/java/com/example/demo/model/ContentType.java
package com.example.demo.model;

public enum ContentType {
    VIDEO,
    PDF,
    TEXT
}
```

```java
// src/main/java/com/example/demo/model/Content.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Content {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String title;
    
    @Enumerated(EnumType.STRING)
    private ContentType type;
    
    @Column(length = 4000)
    private String textContent; // Used for TEXT type
    
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private FileMetadata file; // Used for VIDEO and PDF types
    
    @ManyToOne
    private Lesson lesson;
    
    private Integer orderIndex; // For ordering content within a lesson
}
```

```java
// src/main/java/com/example/demo/model/FileMetadata.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String originalFilename;
    private String storedFilename; // Unique name in the file system
    private String filePath;       // Relative path in storage
    private String contentType;    // MIME type
    private Long fileSize;         // Size in bytes
}
```

## 9. Exam Models

```java
// src/main/java/com/example/demo/model/Exam.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exam {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    private Integer timeLimit; // in minutes, optional
    
    private Integer passingScore; // minimum score to pass
    
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();
}
```

```java
// src/main/java/com/example/demo/model/Question.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @Column(length = 1000)
    private String text;
    
    private Integer points;
    
    @ManyToOne
    private Exam exam;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    private List<Answer> answers = new ArrayList<>();
}
```

```java
// src/main/java/com/example/demo/model/Answer.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Answer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @Column(length = 1000)
    private String text;
    
    private boolean correct;
}
```

```java
// src/main/java/com/example/demo/model/Submission.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @ManyToOne
    private User student;
    
    @ManyToOne
    private Exam exam;
    
    private LocalDateTime submissionTime;
    
    private Integer score;
    
    private boolean passed;
    
    // Store question ID -> answer ID mapping
    @ElementCollection
    @CollectionTable(name = "submission_answers", 
        joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_id")
    @Column(name = "answer_id")
    private Map<Long, Long> answers = new HashMap<>();
}
```

## 10. Progress Tracking Model

```java
// src/main/java/com/example/demo/model/Progress.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Progress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @ManyToOne
    private User student;
    
    @ManyToOne
    private Course course;
    
    // Track completed lessons
    @ElementCollection
    @CollectionTable(name = "completed_lessons", 
        joinColumns = @JoinColumn(name = "progress_id"))
    @Column(name = "lesson_id")
    private Set<Long> completedLessons = new HashSet<>();
    
    // Track viewed content
    @ElementCollection
    @CollectionTable(name = "viewed_content", 
        joinColumns = @JoinColumn(name = "progress_id"))
    @Column(name = "content_id")
    private Set<Long> viewedContent = new HashSet<>();
    
    private LocalDateTime lastAccessed;
    
    // Cache calculated values for performance
    private Integer totalLessons;
    private Integer completedLessonCount;
    private Double completionPercentage;
}
```

## 11. File Storage Service (Hybrid Approach)

```java
// src/main/java/com/example/demo/service/FileStorageService.java
package com.example.demo.service;

import com.example.demo.model.FileMetadata;
import com.example.demo.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final FileMetadataRepository fileMetadataRepository;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public FileMetadata storeFile(MultipartFile file, String subdirectory) {
        // Generate unique filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
        
        try {
            // Create subdirectory if it doesn't exist
            Path targetLocation = this.fileStorageLocation.resolve(subdirectory);
            Files.createDirectories(targetLocation);
            
            // Store the file
            Path filePath = targetLocation.resolve(storedFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Create and save metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoredFilename(storedFilename);
            metadata.setFilePath(subdirectory + "/" + storedFilename);
            metadata.setContentType(file.getContentType());
            metadata.setFileSize(file.getSize());
            
            return fileMetadataRepository.save(metadata);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFilename, ex);
        }
    }

    public Resource loadFileAsResource(String filePath) {
        try {
            Path file = this.fileStorageLocation.resolve(filePath).normalize();
            Resource resource = new UrlResource(file.toUri());
            
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found: " + filePath, ex);
        }
    }
    
    public boolean deleteFile(FileMetadata metadata) {
        try {
            Path file = this.fileStorageLocation.resolve(metadata.getFilePath()).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new RuntimeException("Error deleting file: " + metadata.getFilePath(), ex);
        }
    }
    
    // Generate subdirectory path based on course and lesson IDs
    public String generatePath(Long courseId, Long lessonId, String type) {
        return String.format("courses/%d/lessons/%d/%s", courseId, lessonId, type);
    }
}
```

## 12. Repositories (Examples)

```java
// src/main/java/com/example/demo/repository/UserRepository.java
package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

```java
// src/main/java/com/example/demo/repository/CourseRepository.java
package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacher(User teacher);
    List<Course> findByEnrolledStudentsContaining(User student);
}
```

```java
// src/main/java/com/example/demo/repository/ProgressRepository.java
package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends JpaRepository<Progress, Long> {
    List<Progress> findByStudent(User student);
    Optional<Progress> findByStudentAndCourse(User student, Course course);
}
```

## 13. Services (Examples)

```java
// src/main/java/com/example/demo/service/UserService.java
package com.example.demo.service;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user, boolean isTeacher) {
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Assign role
        Role role = isTeacher ? 
                roleRepository.findByName("ROLE_TEACHER")
                    .orElseThrow(() -> new RuntimeException("Role not found")) :
                roleRepository.findByName("ROLE_STUDENT")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
        
        user.setRoles(Set.of(role));
        
        return userRepository.save(user);
    }
    
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    // Other methods as needed...
}
```

```java
// src/main/java/com/example/demo/service/CourseService.java
package com.example.demo.service;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.ProgressRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;

    public CourseService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
    }

    public Course createCourse(Course course, User teacher) {
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }
    
    public List<Course> getTeacherCourses(User teacher) {
        return courseRepository.findByTeacher(teacher);
    }
    
    public List<Course> getEnrolledCourses(User student) {
        return courseRepository.findByEnrolledStudentsContaining(student);
    }
    
    public Course enrollStudent(Long courseId, User student) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        
        if (!course.getEnrolledStudents().contains(student)) {
            course.getEnrolledStudents().add(student);
            courseRepository.save(course);
            
            // Initialize progress tracking
            Progress progress = new Progress();
            progress.setStudent(student);
            progress.setCourse(course);
            progress.setLastAccessed(LocalDateTime.now());
            progress.setTotalLessons(course.getLessons().size());
            progress.setCompletedLessonCount(0);
            progress.setCompletionPercentage(0.0);
            progressRepository.save(progress);
        }
        
        return course;
    }
    
    // Other methods as needed...
}
```

## 14. REST Controllers (Examples)

```java
// src/main/java/com/example/demo/controller/AuthController.java
package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register/student")
    public ResponseEntity<?> registerStudent(@RequestBody User user) {
        User savedUser = userService.registerUser(user, false); // false = not a teacher
        return ResponseEntity.ok("Student registered successfully");
    }

    @PostMapping("/register/teacher")
    public ResponseEntity<?> registerTeacher(@RequestBody User user) {
        User savedUser = userService.registerUser(user, true); // true = is a teacher
        return ResponseEntity.ok("Teacher registered successfully");
    }
}
```

```java
// src/main/java/com/example/demo/controller/CourseController.java
package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final UserService userService;

    public CourseController(
            CourseService courseService,
            UserService userService) {
        this.courseService = courseService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(
            @RequestBody Course course,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Course savedCourse = courseService.createCourse(course, teacher);
        return ResponseEntity.ok(savedCourse);
    }

    @GetMapping("/teaching")
    public ResponseEntity<List<Course>> getTeacherCourses(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getTeacherCourses(teacher);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/enrolled")
    public ResponseEntity<List<Course>> getEnrolledCourses(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getEnrolledCourses(student);
        return ResponseEntity.ok(courses);
    }

    @PostMapping("/{courseId}/enroll")
    public ResponseEntity<Course> enrollInCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.enrollStudent(courseId, student);
        return ResponseEntity.ok(course);
    }
    
    // Other endpoints as needed...
}
```

```java
// src/main/java/com/example/demo/controller/ContentController.java
package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.service.ContentService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.LessonService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final FileStorageService fileStorageService;

    public ContentController(
            ContentService contentService,
            LessonService lessonService,
            FileStorageService fileStorageService) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Content> uploadContent(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lessonId") Long lessonId,
            @RequestParam("title") String title,
            @RequestParam("contentType") ContentType contentType,
            @RequestParam("orderIndex") Integer orderIndex) {

        Lesson lesson = lessonService.getLessonById(lessonId);
        
        // Create content with file
        Content content = new Content();
        content.setTitle(title);
        content.setType(contentType);
        content.setLesson(lesson);
        content.setOrderIndex(orderIndex);
        
        // Generate path based on course and lesson ID
        String subdirectory = fileStorageService.generatePath(
                lesson.getCourse().getId(), 
                lessonId, 
                contentType.toString().toLowerCase());
        
        // Store file and get metadata
        FileMetadata metadata = fileStorageService.storeFile(file, subdirectory);
        content.setFile(metadata);
        
        Content savedContent = contentService.saveContent(content);
        return ResponseEntity.ok(savedContent);
    }

    @PostMapping("/text")
    public ResponseEntity<Content> createTextContent(
            @RequestParam("lessonId") Long lessonId,
            @RequestParam("title") String title,
            @RequestParam("textContent") String textContent,
            @RequestParam("orderIndex") Integer orderIndex) {

        Lesson lesson = lessonService.getLessonById(lessonId);
        
        Content content = new Content();
        content.setTitle(title);
        content.setType(ContentType.TEXT);
        content.setTextContent(textContent);
        content.setLesson(lesson);
        content.setOrderIndex(orderIndex);
        
        Content savedContent = contentService.saveContent(content);
        return ResponseEntity.ok(savedContent);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long fileId,
            HttpServletRequest request) {
        
        FileMetadata metadata = contentService.getFileMetadataById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());
        
        // Try to determine content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // Logger could be used here
        }
        
        // Fallback to the content type stored in metadata
        if (contentType == null) {
            contentType = metadata.getContentType();
        }
        
        // For videos, use content-disposition: inline to enable streaming
        String disposition = metadata.getContentType().startsWith("video/") ? 
                "inline; filename=\"" + metadata.getOriginalFilename() + "\"" : 
                "attachment; filename=\"" + metadata.getOriginalFilename() + "\"";
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }
    
    // Other endpoints as needed...
}
```

## 15. Progress Tracking Controller 

```java
// src/main/java/com/example/demo/controller/ProgressController.java
package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.ProgressService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final UserService userService;
    private final CourseService courseService;

    public ProgressController(
            ProgressService progressService,
            UserService userService,
            CourseService courseService) {
        this.progressService = progressService;
        this.userService = userService;
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<List<Progress>> getStudentProgress(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Progress> progressList = progressService.getProgressByStudent(student);
        return ResponseEntity.ok(progressList);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<Progress> getCourseProgress(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);
        Progress progress = progressService.getOrCreateProgress(student, course);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/lesson/{lessonId}/complete")
    public ResponseEntity<Progress> markLessonComplete(
            @PathVariable Long lessonId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markLessonComplete(student, lessonId);
        return ResponseEntity.ok(updatedProgress);
    }

    @PostMapping("/content/{contentId}/view")
    public ResponseEntity<Progress> markContentViewed(
            @PathVariable Long contentId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markContentViewed(student, contentId);
        return ResponseEntity.ok(updatedProgress);
    }
}
```

## 16. View Controller (For Thymeleaf Views in the Hybrid Approach)

```java
// src/main/java/com/example/demo/controller/ViewController.java
package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.ProgressService;
import com.example.demo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@Controller
public class ViewController {

    private final UserService userService;
    private final CourseService courseService;
    private final ProgressService progressService;

    public ViewController(
            UserService userService,
            CourseService courseService,
            ProgressService progressService) {
        this.userService = userService;
        this.courseService = courseService;
        this.progressService = progressService;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userService.findByUsername(authentication.getName());
        
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_TEACHER"))) {
            List<Course> courses = courseService.getTeacherCourses(user);
            model.addAttribute("courses", courses);
            return "teacher-dashboard";
        } else {
            List<Course> courses = courseService.getEnrolledCourses(user);
            List<Progress> progressList = progressService.getProgressByStudent(user);
            
            model.addAttribute("courses", courses);
            model.addAttribute("progressList", progressList);
            return "student-dashboard";
        }
    }

    @GetMapping("/courses/{courseId}")
    public String viewCourse(@PathVariable Long courseId, Authentication authentication, Model model) {
        User user = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);
        
        model.addAttribute("course", course);
        
        // For students, add progress information
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_STUDENT"))) {
            Progress progress = progressService.getOrCreateProgress(user, course);
            model.addAttribute("progress", progress);
        }
        
        return "course-details";
    }
    
    // Other view mappings as needed...
}
```

## 17. Next Steps and Implementation Plan

1. **Database Initialization**
   - Create a DataInitializer class to populate initial roles (ROLE_TEACHER, ROLE_STUDENT)
   - Add sample users for testing

2. **Templates Development**
   - Create basic Thymeleaf templates for the main views
   - Implement fragments for header, footer, navigation

3. **JavaScript for Dynamic Functionality**
   - Implement JavaScript to interact with REST endpoints
   - Add AJAX for asynchronous interactions

4. **Testing**
   - Manual testing of core functionality
   - Validate all user flows

5. **Refinement**
   - Improve UI/UX
   - Add validation and error handling
   - Optimize performance

## 18. Time Estimate

- Initial setup and configuration: 1-2 days
- Core entity implementation: 2-3 days
- API development: 3-4 days
- File handling implementation: 1-2 days
- Thymeleaf templates and controllers: 2-3 days
- JavaScript for dynamic content: 2-3 days
- Testing and refinement: 2-3 days

Total: 2-3 weeks for a functional system
