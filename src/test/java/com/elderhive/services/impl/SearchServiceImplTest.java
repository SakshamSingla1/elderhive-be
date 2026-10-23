package com.elderhive.services.impl;

import com.elderhive.dtos.Search.CaretakerSearchResult;
import com.elderhive.dtos.Search.ElderProfileSearchResult;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ElderProfileRepository elderProfileRepository;

    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;

    @InjectMocks
    private SearchServiceImpl service;

    private User user(Long id, String fullName, UserTypeEnum type) {
        return User.builder().id(id).email("u" + id + "@example.com").fullName(fullName)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private CaretakerProfile caretaker(Long id, Long userId, Double lat, Double lon, Set<ServiceCategory> specialties) {
        return CaretakerProfile.builder().id(id).userId(userId).latitude(lat).longitude(lon)
                .ratingAverage(4.5).verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).specialties(specialties).build();
    }

    private ServiceCategory category(Long id, String name) {
        return ServiceCategory.builder().id(id).name(name).status(ServiceCategoryStatusEnum.ACTIVE).build();
    }

    // ==== searchCaretakers ====

    @Test
    void blankQueryAndNoCoordsReturnsEmptyWithoutHittingTheRepository() {
        List<CaretakerSearchResult> results = service.searchCaretakers(null, null, null, null);

        assertThat(results).isEmpty();
        verify(caretakerProfileRepository, never()).searchByFullNamePattern(any(), any());
    }

    @Test
    void aTooShortQueryWithNoCoordsReturnsEmpty() {
        List<CaretakerSearchResult> results = service.searchCaretakers("a", null, null, null);

        assertThat(results).isEmpty();
        verify(caretakerProfileRepository, never()).searchByFullNamePattern(any(), any());
    }

    @Test
    void searchingByNameReturnsMatchingCaretakersWithNoDistance() {
        CaretakerProfile profile = caretaker(100L, 1L, null, null, Set.of(category(1L, "Mobility Assistance")));
        when(caretakerProfileRepository.searchByFullNamePattern(eq("%car%"), any())).thenReturn(List.of(profile));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user(1L, "Carla Caretaker", UserTypeEnum.CARETAKER)));

        List<CaretakerSearchResult> results = service.searchCaretakers("car", null, null, null);

        assertThat(results).hasSize(1);
        CaretakerSearchResult result = results.get(0);
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Carla Caretaker");
        assertThat(result.getSpecialtiesSummary()).isEqualTo("Mobility Assistance");
        assertThat(result.getDistanceKm()).isNull();
        assertThat(result.getVerificationStatus()).isEqualTo(CaretakerVerificationStatusEnum.VERIFIED);
    }

    @Test
    void blankQueryWithCoordsStillSearchesSortedByDistance() {
        CaretakerProfile near = caretaker(1L, 1L, 12.9716, 77.5946, Set.of());
        CaretakerProfile far = caretaker(2L, 2L, 28.6139, 77.2090, Set.of());
        CaretakerProfile noLocation = caretaker(3L, 3L, null, null, Set.of());
        when(caretakerProfileRepository.searchByFullNamePattern(eq("%%"), any())).thenReturn(List.of(far, near, noLocation));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(1L, "Near", UserTypeEnum.CARETAKER), user(2L, "Far", UserTypeEnum.CARETAKER), user(3L, "NoLoc", UserTypeEnum.CARETAKER)
        ));

        List<CaretakerSearchResult> results = service.searchCaretakers(null, 12.9716, 77.5946, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getName()).isEqualTo("Near");
        assertThat(results.get(0).getDistanceKm()).isEqualTo(0.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(1).getName()).isEqualTo("Far");
        // Caretakers with no location set sort last, distance null.
        assertThat(results.get(2).getName()).isEqualTo("NoLoc");
        assertThat(results.get(2).getDistanceKm()).isNull();
    }

    @Test
    void limitIsClampedToTheConfiguredMaximum() {
        when(caretakerProfileRepository.searchByFullNamePattern(any(), any())).thenReturn(List.of());

        service.searchCaretakers("carla", null, null, 9999);

        verify(caretakerProfileRepository).searchByFullNamePattern(eq("%carla%"), eq(org.springframework.data.domain.PageRequest.of(0, 25)));
    }

    @Test
    void specialtiesSummaryIsCappedAndCountsTheRest() {
        CaretakerProfile profile = caretaker(100L, 1L, null, null, Set.of(
                category(1L, "Mobility Assistance"), category(2L, "Medication Management"),
                category(3L, "Meal Prep"), category(4L, "Companionship")));
        when(caretakerProfileRepository.searchByFullNamePattern(any(), any())).thenReturn(List.of(profile));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1L, "Carla", UserTypeEnum.CARETAKER)));

        List<CaretakerSearchResult> results = service.searchCaretakers("carla", null, null, null);

        assertThat(results.get(0).getSpecialtiesSummary()).contains("+1 more");
    }

    // ==== searchElderProfiles ====

    @Test
    void ownedElderProfilesAreLabeledOwner() {
        User caller = user(10L, "Family Caller", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile owned = ElderProfile.builder().id(1L).familyUserId(10L).fullName("Grandma Rose").build();
        when(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(10L)).thenReturn(List.of(owned));
        when(elderProfileRepository.findByElderUserId(10L)).thenReturn(Optional.empty());

        List<ElderProfileSearchResult> results = service.searchElderProfiles(null, null, caller);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRelationship()).isEqualTo("Owner");
        assertThat(results.get(0).getName()).isEqualTo("Grandma Rose");
    }

    @Test
    void coManagedElderProfilesUseTheirFamilyElderLinkRelationshipLabel() {
        User caller = user(20L, "Co Manager", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile coManaged = ElderProfile.builder().id(2L).familyUserId(99L).fullName("Grandpa Joe").build();
        when(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(20L)).thenReturn(List.of(coManaged));
        when(elderProfileRepository.findByElderUserId(20L)).thenReturn(Optional.empty());
        when(familyElderLinkRepository.findByElderProfileIdAndFamilyUserId(2L, 20L))
                .thenReturn(Optional.of(FamilyElderLink.builder().relationshipLabel("Son").build()));

        List<ElderProfileSearchResult> results = service.searchElderProfiles(null, null, caller);

        assertThat(results.get(0).getRelationship()).isEqualTo("Son");
    }

    @Test
    void theElderThemselvesIsLabeledElder() {
        User caller = user(30L, "Self Elder", UserTypeEnum.ELDER);
        ElderProfile self = ElderProfile.builder().id(3L).elderUserId(30L).fullName("Self Elder").build();
        when(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(30L)).thenReturn(List.of());
        when(elderProfileRepository.findByElderUserId(30L)).thenReturn(Optional.of(self));

        List<ElderProfileSearchResult> results = service.searchElderProfiles(null, null, caller);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRelationship()).isEqualTo("Elder");
    }

    @Test
    void queryFiltersByNameCaseInsensitively() {
        User caller = user(10L, "Family Caller", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile rose = ElderProfile.builder().id(1L).familyUserId(10L).fullName("Grandma Rose").build();
        ElderProfile joe = ElderProfile.builder().id(2L).familyUserId(10L).fullName("Grandpa Joe").build();
        when(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(10L)).thenReturn(List.of(rose, joe));
        when(elderProfileRepository.findByElderUserId(10L)).thenReturn(Optional.empty());

        List<ElderProfileSearchResult> results = service.searchElderProfiles("rose", null, caller);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Grandma Rose");
    }

    @Test
    void aCallerWithNoAccessibleProfilesGetsAnEmptyList() {
        User caller = user(40L, "Nobody", UserTypeEnum.FAMILY_MEMBER);
        when(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(40L)).thenReturn(List.of());
        when(elderProfileRepository.findByElderUserId(40L)).thenReturn(Optional.empty());

        List<ElderProfileSearchResult> results = service.searchElderProfiles(null, null, caller);

        assertThat(results).isEmpty();
    }
}
