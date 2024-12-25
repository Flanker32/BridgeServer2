package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.ValidatorUtils.MEDIUMTEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.google.common.base.Strings;

@Component
public class ConsentSignatureValidator implements Validator {
    
    private static final String TOO_YOUNG = "too recent (the study requires participants to be %s years of age or older).";
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    private static final String CANNOT_BE_EMPTY_STRING = "cannot be an empty string";

    private static final int LEAP_YEAR_MONTH = 2;
    private static final int LEAP_YEAR_DAY = 29;

    private final int minAgeOfConsent;

    public ConsentSignatureValidator(int minAgeOfConsent) {
        this.minAgeOfConsent = minAgeOfConsent;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return ConsentSignature.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ConsentSignature sig = (ConsentSignature) target;

        if (Strings.isNullOrEmpty(sig.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, sig.getName(), "name");
        if (Strings.isNullOrEmpty(sig.getBirthdate())) {
            if (minAgeOfConsent > 0) {
                errors.rejectValue("birthdate", CANNOT_BE_BLANK);
            }
        } else {
            LocalDate birthdate = parseBirthday(sig.getBirthdate());
            if (birthdate == null) {
                errors.rejectValue("birthdate", "is invalid (required format: YYYY-MM-DD)");
            } else if (minAgeOfConsent > 0) {
                // A valid birthdate was provided, ensure the user is old enough
                LocalDate now = LocalDate.now(DateTimeZone.UTC);
                Period period = new Period(birthdate, now);

                if (period.getYears() < minAgeOfConsent || bissextileBirthdayAgeCheck(birthdate, now)) {
                    String message = TOO_YOUNG.formatted(minAgeOfConsent);
                    errors.rejectValue("birthdate", message);
                }
            }
        }
        if (sig.getSignedOn() <= 0L) {
            errors.rejectValue("signedOn", "must be a valid signature timestamp");
        }
        // Signature image is currently optional. Some apps may collect a signature, but some may not. It's okay
        // to let the client validate this until we're sure this 100% required for all consents.
        String imageData = sig.getImageData();
        String imageMimeType = sig.getImageMimeType();
        if (imageData != null && imageData.isEmpty()) {
            errors.rejectValue("imageData", CANNOT_BE_EMPTY_STRING);
        }
        validateStringLength(errors, MEDIUMTEXT_SIZE, imageData, "imageData");
        if (imageMimeType != null && imageMimeType.isEmpty()) {
            errors.rejectValue("imageMimeType", CANNOT_BE_EMPTY_STRING);
        }
        validateStringLength(errors, 255, imageMimeType, "imageMimeType");

        // if one of them is not null, but not both
        if (imageData != null ^ imageMimeType != null) {
            errors.reject("must specify imageData and imageMimeType if you specify either of them");
        }
    }

    // Add additional check to handle participants' birthdays on 2/29 of a leap year and current day is 2/28 of 18
    // years later - e.g. Joda thinks there are 18 years between 2/29/2004 and 2/28/2022, but it won't be
    // 18 years until 3/1/2022. (Jira issue: https://sagebionetworks.jira.com/browse/BRIDGE-2136)
    private boolean bissextileBirthdayAgeCheck(LocalDate birthdate, LocalDate now) {
        Period period = new Period(birthdate, now);
        return (period.getYears() == minAgeOfConsent &&
                now.getMonthOfYear() == LEAP_YEAR_MONTH &&
                birthdate.getMonthOfYear() == LEAP_YEAR_MONTH &&
                birthdate.getDayOfMonth() == LEAP_YEAR_DAY);
    }

    private LocalDate parseBirthday(String birthdate) {
        if (birthdate != null) {
            try {
                return LocalDate.parse(birthdate);
            } catch(IllegalArgumentException e) {
            }
        }
        return null;
    }
}
