package com.playdata.userservice.user.service;

import com.playdata.userservice.common.auth.TokenUserInfo;
import com.playdata.userservice.user.dto.UserLoginReqDto;
import com.playdata.userservice.user.dto.UserResDto;
import com.playdata.userservice.user.dto.UserSaveReqDto;
import com.playdata.userservice.user.dto.UserUpdateRequestDto;
import com.playdata.userservice.user.entity.User;
import com.playdata.userservice.user.entity.UserStatus;
import com.playdata.userservice.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    //필요한 객체 생성하여 주입
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final MailSenderService mailSenderService;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key 상수
    private static final String VERIFYCATION_CODE_KEY = "email_verify:code:";
    private static final String VERIFYCATION_ATTEMPT_KEY = "email_verify:attempt:";
    private static final String VERIFYCATION_BLOCK_KEY = "email_verify:block:";



    public User userCreate(UserSaveReqDto dto) {
        Optional<User> foundEmail
                = userRepository.findByEmail(dto.getEmail());

        if (foundEmail.isPresent()) {

            throw new IllegalArgumentException("이미 존재하는 이메일 입니다!");
        }


        User user = dto.toEntity(encoder);
        User saved = userRepository.save(user);
        return saved;
    }

    public User login(UserLoginReqDto dto) {

        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow(
                () -> new EntityNotFoundException("User not found!")
        );

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("비활성화된 계정입니다. (탈퇴 또는 정지)");
        }


        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    public UserResDto myInfo() {
        TokenUserInfo userInfo

                = (TokenUserInfo) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User user = userRepository.findByEmail(userInfo.getEmail())
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found!")
                );

        return user.fromEntity();
    }

    public User updateUser(Long userId, UserUpdateRequestDto dto) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("회원 정보를 찾을 수 없습니다.")
        );
        if (!user.getEmail().equals(dto.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("이미 사용중인 이메일 입니다.");
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            String encodedPassword = encoder.encode(dto.getPassword());
            user.setPassword(encodedPassword);
        }
        user.setName(dto.getName());
        user.setAddress(dto.getAddress());
        user.setPhone(dto.getPhone());

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("탈퇴 실행 시작 userId={}", userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("회원 없음"));

        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);

        log.info("탈퇴 완료: userId={}, status={}", userId, user.getStatus());
    }

    public void restoreUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("회원 없음"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new IllegalArgumentException("이미 활성화된 사용자입니다.");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public List<UserResDto> userList(Pageable pageable) {

        Page<User> users = userRepository.findAll(pageable);

        List<User> content = users.getContent();
        List<UserResDto> dtoList = content.stream()
                .map(User::fromEntity)
                .collect(Collectors.toList());

        return dtoList;

    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found!"));
    }

    public UserResDto findByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new EntityNotFoundException("User not found!")
        );
        return user.fromEntity();
    }

    public User updateUserAddress(Long userId, String address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAddress(address);
        return userRepository.save(user);
    }

    public String mailCheck(String email) {

        // 차단 상태 확인
        if(isBlocked(email)){
            throw new IllegalArgumentException("잘못된 요청 횟수가 과다하여 임시 차단 중입니다. 잠시 후에 시도해주세요.");
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            throw new IllegalArgumentException("이미 존재하는 이메일 입니다.");
        });
        String authNum;
        //이메일 전송만을 담당하는 객체를 이용해서 이메일 로직 작성.
        try {
             authNum = mailSenderService.joinMail(email);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 과정 중 문제 발생!");
        }

        //인증 코드 redis 에 저장
        String key = VERIFYCATION_CODE_KEY + email;
        redisTemplate.opsForValue().set(key, authNum, Duration.ofMinutes(1));
        return authNum;
    }


    //인증코드 검증 로직
    public Map<String, String> verifyEmail(Map<String, String> map) {

        // 차단 상태 확인
        if(isBlocked(map.get("email"))){
            throw new IllegalArgumentException("잘못된 횟수가 과다하여 임시 차단 중입니다. 잠시 후에 시도해주세요.");
        }


        // 인증 코드 조회
        String key = VERIFYCATION_CODE_KEY + map.get("email");
        Object foundCode = redisTemplate.opsForValue().get(key);
        if (foundCode == null) {
            throw new IllegalArgumentException("인증 코드가 만료 되었습니다.");
        }

        // 인증 시도 횟수 증가
        int attemptCount = incrementAttemptCount(map.get("email"));

        // 조회한 코드와 사용자가 입력한 인증번호 검증
        if(!foundCode.toString().equals(map.get("code"))) {
            //최대 시도 횟수 초과시 차단
            if(attemptCount >= 3){
                blockUser(map.get("email"));
                throw new IllegalArgumentException("email blocked");
            }
            int remainingAttempts = 3 - attemptCount;

            throw new IllegalArgumentException(String.format("인증 코드가 올바르지 않습니다!, %d", remainingAttempts));
        }

        log.info("이메일 인증 성공!, email={}", map.get("email"));
        redisTemplate.delete(key); // 레디스에서 인증번호 삭제
        return map;
    }

    private boolean isBlocked(String email){
        String key = VERIFYCATION_BLOCK_KEY + email;
        return redisTemplate.hasKey(key);
    }

    private void blockUser(String email) {
        String key = VERIFYCATION_BLOCK_KEY + email;
        redisTemplate.opsForValue().set(key, "blocked", Duration.ofMinutes(30));
    }

    private int incrementAttemptCount(String email){
        String key = VERIFYCATION_ATTEMPT_KEY + email;
        Object obj = redisTemplate.opsForValue().get(key);

        int count = (obj != null) ? Integer.parseInt(obj.toString()) +1 : 1;
        redisTemplate.opsForValue().set(key, count, Duration.ofMinutes(1));
        return count;
    }
}









