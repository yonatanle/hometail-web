# ViewAnimals.xhtml Apply Button Fix

## Issue Identified
The "Apply" button was hanging due to a serialization issue with the `ObjectMapper` in the `@ViewScoped` bean.

## Root Cause
1. **Transient ObjectMapper**: The `ObjectMapper` was marked as `transient` but wasn't being properly re-initialized after bean deserialization
2. **Missing Null Checks**: Methods using the mapper didn't check if it was null before use
3. **ViewScoped Serialization**: JSF ViewScoped beans get serialized/deserialized during the request lifecycle, causing transient fields to become null

## Fixes Applied

### 1. ViewAnimalsBean.java Changes
- Added `initializeMapper()` method to safely initialize the ObjectMapper
- Added null checks and mapper initialization in `reloadFromBackend()` and `loadCategoriesFromBackend()`
- Added helper method `getCategory()` for proper category name display
- Improved error handling and logging

### 2. viewAnimals.xhtml Changes
- Added loading dialog with spinner for better user feedback
- Enhanced AJAX error handling with proper dialog management
- Added visual feedback during Apply button processing

## Key Changes Made

### Backend (ViewAnimalsBean.java)
```java
private void initializeMapper() {
    if (mapper == null) {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
```

### Frontend (viewAnimals.xhtml)
```xml
<p:commandButton id="applyBtn"
                 value="Apply"
                 action="#{viewAnimalsBean.applyFilters}"
                 ajax="true"
                 process="@form"
                 update=":animalsForm:results :animalsForm:msgs"
                 onstart="PF('statusDialog').show(); console.log('ajax start')"
                 oncomplete="PF('statusDialog').hide(); console.log('ajax done')"
                 onerror="PF('statusDialog').hide(); console.error('ajax error', arguments);" />
```

## Expected Behavior After Fix
1. Apply button should work without hanging
2. Loading dialog appears during processing
3. Proper error messages displayed if backend is unavailable
4. Smooth AJAX updates of the results table
5. Console logging for debugging purposes

## Testing Instructions
1. Deploy the updated application
2. Navigate to viewAnimals.xhtml
3. Set some filter criteria
4. Click "Apply" button
5. Verify the page doesn't hang and results are updated
6. Check browser console for proper AJAX lifecycle logging