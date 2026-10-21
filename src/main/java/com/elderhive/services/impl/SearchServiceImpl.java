package com.elderhive.services.impl;

import com.elderhive.dtos.Search.CaretakerSearchResult;
import com.elderhive.dtos.Search.ElderProfileSearchResult;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Both endpoints are deliberately lightweight (debounced-autocomplete-friendly per this
 * prompt's own text), not Phase 9's eventual full filterable search - a bounded in-memory
 * overfetch-then-sort for distance, not a SQL-level haversine WHERE clause (that level of
 * rigor is Phase 11 prompt 82's job, for emergency proximity matching at real scale).
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int CARETAKER_OVERFETCH_CAP = 200;
    private static final int MAX_SPECIALTIES_IN_SUMMARY = 3;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;

    @Override
    public List<CaretakerSearchResult> searchCaretakers(String query, Double latitude, Double longitude, Integer limit) {
        int effectiveLimit = clampLimit(limit);
        boolean hasQuery = hasMinLength(query);
        boolean hasCoords = latitude != null && longitude != null;
        if (!hasQuery && !hasCoords) {
            return List.of();
        }

        String pattern = "%" + (hasQuery ? query.trim().toLowerCase() : "") + "%";
        int fetchSize = hasCoords ? CARETAKER_OVERFETCH_CAP : effectiveLimit;
        List<CaretakerProfile> profiles = caretakerProfileRepository.searchByFullNamePattern(pattern, PageRequest.of(0, fetchSize));

        Map<Long, User> usersByUserId = userRepository.findAllById(
                profiles.stream().map(CaretakerProfile::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<CaretakerSearchResult> results = profiles.stream()
                .map(profile -> toCaretakerResult(profile, usersByUserId.get(profile.getUserId()), latitude, longitude, hasCoords))
                .collect(Collectors.toCollection(ArrayList::new));

        if (hasCoords) {
            results.sort(Comparator.comparing(CaretakerSearchResult::getDistanceKm,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }

        return results.size() > effectiveLimit ? results.subList(0, effectiveLimit) : results;
    }

    @Override
    public List<ElderProfileSearchResult> searchElderProfiles(String query, Integer limit, User caller) {
        int effectiveLimit = clampLimit(limit);
        boolean hasQuery = hasMinLength(query);
        String normalizedQuery = hasQuery ? query.trim().toLowerCase() : null;

        Set<ElderProfile> accessible = new LinkedHashSet<>(elderProfileRepository.findOwnedOrCoManagedByFamilyUserId(caller.getId()));
        elderProfileRepository.findByElderUserId(caller.getId()).ifPresent(accessible::add);

        return accessible.stream()
                .filter(profile -> normalizedQuery == null
                        || (profile.getFullName() != null && profile.getFullName().toLowerCase().contains(normalizedQuery)))
                .sorted(Comparator.comparing(ElderProfile::getFullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(effectiveLimit)
                .map(profile -> toElderProfileResult(profile, caller))
                .toList();
    }

    private CaretakerSearchResult toCaretakerResult(CaretakerProfile profile, User user, Double latitude, Double longitude, boolean hasCoords) {
        Double distanceKm = (hasCoords && profile.getLatitude() != null && profile.getLongitude() != null)
                ? haversineKm(latitude, longitude, profile.getLatitude(), profile.getLongitude())
                : null;
        return CaretakerSearchResult.builder()
                .id(profile.getId())
                .name(user != null ? user.getFullName() : null)
                .specialtiesSummary(summarizeSpecialties(profile.getSpecialties()))
                .ratingAverage(profile.getRatingAverage())
                .distanceKm(distanceKm)
                .verificationStatus(profile.getVerificationStatus())
                .build();
    }

    private String summarizeSpecialties(Set<String> specialties) {
        if (specialties == null || specialties.isEmpty()) {
            return null;
        }
        List<String> sorted = specialties.stream().sorted().toList();
        if (sorted.size() <= MAX_SPECIALTIES_IN_SUMMARY) {
            return String.join(", ", sorted);
        }
        String shown = String.join(", ", sorted.subList(0, MAX_SPECIALTIES_IN_SUMMARY));
        return shown + " +" + (sorted.size() - MAX_SPECIALTIES_IN_SUMMARY) + " more";
    }

    private ElderProfileSearchResult toElderProfileResult(ElderProfile profile, User caller) {
        return ElderProfileSearchResult.builder()
                .id(profile.getId())
                .name(profile.getFullName())
                .relationship(resolveRelationship(profile, caller))
                .build();
    }

    private String resolveRelationship(ElderProfile profile, User caller) {
        if (profile.getElderUserId() != null && profile.getElderUserId().equals(caller.getId())) {
            return "Elder";
        }
        if (profile.getFamilyUserId() != null && profile.getFamilyUserId().equals(caller.getId())) {
            return "Owner";
        }
        return familyElderLinkRepository.findByElderProfileIdAndFamilyUserId(profile.getId(), caller.getId())
                .map(FamilyElderLink::getRelationshipLabel)
                .filter(label -> label != null && !label.isBlank())
                .orElse("Family Member");
    }

    private boolean hasMinLength(String query) {
        return query != null && query.trim().length() >= MIN_QUERY_LENGTH;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
