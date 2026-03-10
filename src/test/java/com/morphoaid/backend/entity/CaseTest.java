package com.morphoaid.backend.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaseTest {

    // UTC-13-TC-01 Associate case with image correctly
    @Test
    void replaceImage_SetsBidirectionalRelationship() {
        // Arrange
        Case aCase = Case.builder().id(100L).build();
        CaseImage oldImage = CaseImage.builder().id(200L).caseEntity(aCase).build();
        aCase.setImage(oldImage); // Simulate existing relationship loaded from DB

        CaseImage newImage = CaseImage.builder().id(201L).build();

        // Act
        aCase.replaceImage(newImage);

        // Assert
        // Verify backward linkage is broken on the old image
        assertNull(oldImage.getCaseEntity(), "Old image should be detached from the case");

        // Verify new relationships are established bi-directionally
        assertSame(newImage, aCase.getImage(), "Case should reference the new image");
        assertSame(aCase, newImage.getCaseEntity(), "New image should reference the case");
    }

    // Extra: Null handling safety check
    @Test
    void replaceImage_WithNull_DetachesSafely() {
        // Arrange
        Case aCase = Case.builder().id(100L).build();
        CaseImage oldImage = CaseImage.builder().id(200L).caseEntity(aCase).build();
        aCase.setImage(oldImage);

        // Act
        aCase.replaceImage(null);

        // Assert
        assertNull(aCase.getImage(), "Case should have no image");
        assertNull(oldImage.getCaseEntity(), "Old image should be detached from the case");
    }
}
