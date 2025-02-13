package gible.domain.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gible.config.KakaoConfig;
import gible.domain.auth.dto.KakaoUserInfo;
import gible.domain.auth.dto.SignInReq;
import gible.exception.CustomException;
import gible.exception.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KakaoService {
    private final KakaoConfig kakaoConfig;
    private final String HEADER_VALUE = "application/x-www-form-urlencoded;charset=utf-8";
    public String getAccessToken(SignInReq signInReq){
        try {
            HttpHeaders headers = new HttpHeaders();
            System.out.println(kakaoConfig.getRedirectUri());
            headers.add("Content-type", HEADER_VALUE);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", kakaoConfig.getClientId());
            body.add("redirect_uri", kakaoConfig.getRedirectUri());
            body.add("code", signInReq.code());
            HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    kakaoConfig.getTokenUri(),
                    HttpMethod.POST,
                    kakaoTokenRequest,
                    String.class
            );
            String responseBody = response.getBody();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.get("access_token").asText();
        } catch(Exception e){
            System.out.println(e.getMessage());
        }
        return null;
    }

    public KakaoUserInfo getUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + accessToken);
            headers.add("Content-Type", HEADER_VALUE);

            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> kakaoUserInfoRequest = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    kakaoConfig.getGetUserInfoUri(),
                    HttpMethod.GET,
                    kakaoUserInfoRequest,
                    String.class
            );
            String responseBody = response.getBody();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode userInfo = objectMapper.readTree(responseBody).get("kakao_account");
            System.out.println(userInfo);

            String email = userInfo.get("email").asText();

            return KakaoUserInfo.of(email);
        } catch (Exception e){
            throw new CustomException(ErrorType.SOCIAL_LOGIN_FAILED);
        }
    }
}
