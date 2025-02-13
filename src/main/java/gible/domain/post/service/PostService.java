package gible.domain.post.service;

import gible.domain.donation.dto.DonationSenderRes;
import gible.domain.donation.entity.Donation;
import gible.domain.donation.repository.DonationRepository;
import gible.domain.mail.service.MailService;
import gible.domain.post.dto.*;
import gible.domain.post.entity.Post;
import gible.domain.post.repository.PostRepository;
import gible.domain.user.entity.User;
import gible.domain.user.repository.UserRepository;
import gible.exception.CustomException;
import gible.exception.error.ErrorType;
import gible.global.aop.annotation.AuthenticatedUser;
import gible.global.util.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static gible.global.util.redis.RedisProperties.NEWEST_EXPIRATION;
import static gible.global.util.redis.RedisProperties.POST_KEY_PREFIX;

@RequiredArgsConstructor
@Service
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MailService mailService;
    private final DonationRepository donationRepository;
    private final RedisUtil redisUtil;

    /* 게시글 생성 */
    @Transactional
    public PostUploadRes savePost(PostReq postReq, UUID userId) {

        User foundUser = userRepository.findById(userId).orElseThrow(() ->
                new CustomException(ErrorType.USER_NOT_FOUND));

        Post post = postReq.toEntity(postReq);
        post.addWriter(foundUser);
        Post savedPost = postRepository.save(post);

        redisUtil.save(POST_KEY_PREFIX + savedPost.getId(), savedPost.getId());
        redisUtil.saveExpire(POST_KEY_PREFIX + savedPost.getId(), NEWEST_EXPIRATION);

        // 등록된 게시글 메일 보내기
        List<User> emailAgreeUsers = userRepository.findByEmailAgree(true);
        mailService.sendMail(emailAgreeUsers.stream().map(User::getEmail).toList(), savedPost);
        return PostUploadRes.from(savedPost.getId());
    }

    /* 전체 게시글 불러오기 */
    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getAllPosts(Pageable pageable) {

        Page<Post> posts = postRepository.findAll(pageable);
        return posts.map(post ->
                PostSummaryRes.fromEntity(post, redisUtil.get(POST_KEY_PREFIX + post.getId())));
    }

    /* 특정 게시글 불러오기 */
    @Transactional(readOnly = true)
    public PostDetailRes getPost(UUID postId, UUID userId) {

        Post foundPost = postRepository.findById(postId).orElseThrow(() ->
                new CustomException(ErrorType.POST_NOT_FOUND));

        boolean isPermitted = userId.equals(foundPost.getWriter().getId());

        List<Donation> donations = donationRepository.findByPost_Id(postId);

        List<DonationSenderRes> donationInfoList = getDonationInfoList(donations, isPermitted);

        return PostDetailRes.fromEntity(foundPost, donationInfoList, isPermitted);
    }

    /* 기부자 정보 불러오기 */
    private List<DonationSenderRes> getDonationInfoList(List<Donation> donations, boolean isPermitted) {
        Map<UUID, DonationSenderRes> donatorInfos = new HashMap<>();

        for (Donation donation : donations) {
            UUID userId = donation.getSender().getId();
            String identifier = isPermitted ? donation.getSender().getName() : donation.getSender().getNickname();
            donatorInfos.merge(userId,
                    DonationSenderRes.of(userId, identifier, donation.getDonateCount()),
                    (existing, newEntry) -> DonationSenderRes.of(
                            existing.userId(),
                            existing.name(),
                            existing.donateCount() + newEntry.donateCount()
                    ));
        }

        return new ArrayList<>(donatorInfos.values());
    }


    /* 검색한 단어에 대한 게시글 불러오기 */
    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getPostsByKeyword(String search, Pageable pageable) {

        Page<Post> searchPosts = postRepository.findByTitleContaining(search, pageable);
        return searchPosts.map(post ->
                PostSummaryRes.fromEntity(post, redisUtil.get(POST_KEY_PREFIX + post.getId())));
    }

    /* 작성자의 게시글 불러오기 */
    @Transactional(readOnly = true)
    public List<PostTitleRes> getPostByUserId(UUID userId) {

        List<Post> posts = postRepository.findByWriter_Id(userId);
        return posts.stream().map(PostTitleRes::fromEntity).toList();
    }

    /* 게시글 수정 */
    @AuthenticatedUser
    @Transactional
    public PostUploadRes updatePost(PostReq postReq, UUID postId) {

        Post foundPost = postRepository.findById(postId).orElseThrow(() ->
                new CustomException(ErrorType.POST_NOT_FOUND));

        foundPost.updatePost(postReq);

        return PostUploadRes.from(foundPost.getId());
    }

    /* 게시글 삭제 */
    @AuthenticatedUser
    @Transactional
    public void deletePost(UUID postId) {

        postRepository.deleteById(postId);
    }

    @Transactional
    public void updateDonationPermitted(UUID postId, boolean isDonationPermitted) {

        postRepository.updateDonationPermittedById(postId, isDonationPermitted);
    }
}
