# SpatialFlow Project Details

This file contains the content of all significant source code and configuration files in the project.

## File: .github\workflows\release.yml

``yaml
name: Android Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      # Use your real SpatialFlow.jks from secrets
      - name: Decode and write release keystore
        run: |
          mkdir -p $HOME/keystores
          echo "$ANDROID_KEYSTORE_BASE64" | base64 --decode > $HOME/keystores/spatialflow.jks
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}

      - name: Set keystore env vars
        run: |
          echo "ANDROID_KEYSTORE_PATH=$HOME/keystores/spatialflow.jks" >> $GITHUB_ENV
          echo "ANDROID_KEYSTORE_PASSWORD=${{ secrets.ANDROID_KEYSTORE_PASSWORD }}" >> $GITHUB_ENV
          echo "ANDROID_KEY_ALIAS=${{ secrets.ANDROID_KEY_ALIAS }}" >> $GITHUB_ENV
          echo "ANDROID_KEY_ALIAS_PASSWORD=${{ secrets.ANDROID_KEY_ALIAS_PASSWORD }}" >> $GITHUB_ENV

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Release APK
        run: ./gradlew assembleRelease

      - name: Generate checksum
        run: |
          cd app/build/outputs/apk/release
          sha256sum app-release.apk > checksum.txt

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            app/build/outputs/apk/release/app-release.apk
            app/build/outputs/apk/release/checksum.txt
          body: |
            ## SpatialFlow ${{ github.ref_name }}
            
            ### What's New
            - Updated app logo
            - Replaced Toast notifications with Snackbar in player
            - Added landscape mode support
            - Added copyright information in Settings page
            
            ### Installation
            1. Download `app-release.apk`
            2. Install on your Android device
            3. Or use in-app "Check for updates" to auto-update
            
            ⚠️ **Note for v1.0.0 users**: You must uninstall the old version first before installing v1.1. Future updates will install seamlessly.
            
            ### Technical Details
            - Min SDK: 24 (Android 7.0+)
            - Target SDK: 35 (Android 15)
            - Version Code: 3
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
``

## File: .idea\AndroidProjectSystem.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="AndroidProjectSystem">
    <option name="providerId" value="com.android.tools.idea.GradleProjectSystem" />
  </component>
</project>
``

## File: .idea\appInsightsSettings.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="AppInsightsSettings">
    <option name="tabSettings">
      <map>
        <entry key="Firebase Crashlytics">
          <value>
            <InsightsFilterSettings>
              <option name="connection">
                <ConnectionSetting>
                  <option name="appId" value="PLACEHOLDER" />
                  <option name="mobileSdkAppId" value="" />
                  <option name="projectId" value="" />
                  <option name="projectNumber" value="" />
                </ConnectionSetting>
              </option>
              <option name="signal" value="SIGNAL_UNSPECIFIED" />
              <option name="timeIntervalDays" value="THIRTY_DAYS" />
              <option name="visibilityType" value="ALL" />
            </InsightsFilterSettings>
          </value>
        </entry>
      </map>
    </option>
  </component>
</project>
``

## File: .idea\compiler.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <bytecodeTargetLevel target="21" />
  </component>
</project>
``

## File: .idea\deploymentTargetSelector.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="deploymentTargetSelector">
    <selectionStates>
      <SelectionState runConfigName="app">
        <option name="selectionMode" value="DROPDOWN" />
        <DropdownSelection timestamp="2025-11-26T13:20:30.856857100Z">
          <Target type="DEFAULT_BOOT">
            <handle>
              <DeviceId pluginId="PhysicalDevice" identifier="serial=LF5LIBMRW8BMJVXO" />
            </handle>
          </Target>
        </DropdownSelection>
        <DialogSelection />
      </SelectionState>
    </selectionStates>
  </component>
</project>
``

## File: .idea\deviceManager.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="DeviceTable">
    <option name="columnSorters">
      <list>
        <ColumnSorterState>
          <option name="column" value="Name" />
          <option name="order" value="ASCENDING" />
        </ColumnSorterState>
      </list>
    </option>
  </component>
</project>
``

## File: .idea\gradle.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleMigrationSettings" migrationVersion="1" />
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="testRunner" value="CHOOSE_PER_TEST" />
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="#GRADLE_LOCAL_JAVA_HOME" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$" />
            <option value="$PROJECT_DIR$/app" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
``

## File: .idea\migrations.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectMigrations">
    <option name="MigrateToGradleLocalJavaHome">
      <set>
        <option value="$PROJECT_DIR$" />
      </set>
    </option>
  </component>
</project>
``

## File: .idea\misc.xml

``xml
<project version="4">
  <component name="ExternalStorageConfigurationManager" enabled="true" />
  <component name="ProjectRootManager" version="2" languageLevel="JDK_21" default="true" project-jdk-name="jbr-21" project-jdk-type="JavaSDK">
    <output url="file://$PROJECT_DIR$/build/classes" />
  </component>
  <component name="ProjectType">
    <option name="id" value="Android" />
  </component>
  <component name="VisualizationToolProject">
    <option name="state">
      <ProjectState>
        <option name="scale" value="0.1496917724609375" />
      </ProjectState>
    </option>
  </component>
</project>
``

## File: .idea\render.experimental.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="RenderSettings">
    <option name="showDecorations" value="true" />
  </component>
</project>
``

## File: .idea\runConfigurations.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="RunConfigurationProducerService">
    <option name="ignoredProducers">
      <set>
        <option value="com.intellij.execution.junit.AbstractAllInDirectoryConfigurationProducer" />
        <option value="com.intellij.execution.junit.AllInPackageConfigurationProducer" />
        <option value="com.intellij.execution.junit.PatternConfigurationProducer" />
        <option value="com.intellij.execution.junit.TestInClassConfigurationProducer" />
        <option value="com.intellij.execution.junit.UniqueIdConfigurationProducer" />
        <option value="com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer" />
        <option value="org.jetbrains.kotlin.idea.junit.KotlinJUnitRunConfigurationProducer" />
        <option value="org.jetbrains.kotlin.idea.junit.KotlinPatternConfigurationProducer" />
      </set>
    </option>
  </component>
</project>
``

## File: .idea\vcs.xml

``xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="VcsDirectoryMappings">
    <mapping directory="" vcs="Git" />
  </component>
</project>
``

## File: app\src\main\res\anim\bounce_in.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:duration="200"
        android:fromXScale="0.8"
        android:fromYScale="0.8"
        android:pivotX="50%"
        android:pivotY="50%"
        android:interpolator="@android:anim/overshoot_interpolator"
        android:toXScale="1.0"
        android:toYScale="1.0" />

    <alpha
        android:duration="200"
        android:fromAlpha="0.7"
        android:toAlpha="1.0" />
</set>
``

## File: app\src\main\res\anim\bounce_out.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:duration="150"
        android:fromXScale="1.0"
        android:fromYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:toXScale="0.9"
        android:toYScale="0.9"
        android:interpolator="@android:anim/anticipate_interpolator" />

    <alpha
        android:duration="150"
        android:fromAlpha="1.0"
        android:toAlpha="0.5" />
</set>
``

## File: app\src\main\res\anim\fragment_cursel_in.xml

``xml
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:shareInterpolator="false">

    <!-- Slide in from right with smooth deceleration -->
    <translate
        android:fromXDelta="12%"
        android:toXDelta="0%"
        android:duration="260"
        android:interpolator="@android:anim/decelerate_interpolator" />

    <!-- Scale up with slight overshoot for playful effect -->
    <scale
        android:fromXScale="0.9"
        android:fromYScale="0.9"
        android:toXScale="1.0"
        android:toYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:duration="260"
        android:interpolator="@android:anim/overshoot_interpolator" />

    <!-- Fade in -->
    <alpha
        android:fromAlpha="0.0"
        android:toAlpha="1.0"
        android:duration="200" />
</set>
``

## File: app\src\main\res\anim\fragment_cursel_in_pop.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:shareInterpolator="false">

    <!-- Slide in from left with smooth deceleration -->
    <translate
        android:fromXDelta="-12%"
        android:toXDelta="0%"
        android:duration="260"
        android:interpolator="@android:anim/decelerate_interpolator" />

    <!-- Scale up with slight overshoot -->
    <scale
        android:fromXScale="0.9"
        android:fromYScale="0.9"
        android:toXScale="1.0"
        android:toYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:duration="260"
        android:interpolator="@android:anim/overshoot_interpolator" />

    <!-- Fade in -->
    <alpha
        android:fromAlpha="0.0"
        android:toAlpha="1.0"
        android:duration="200" />
</set>
``

## File: app\src\main\res\anim\fragment_cursel_out.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:shareInterpolator="false">

    <!-- Slide out to left with acceleration -->
    <translate
        android:fromXDelta="0%"
        android:toXDelta="-12%"
        android:duration="260"
        android:interpolator="@android:anim/accelerate_interpolator" />

    <!-- Scale down slightly -->
    <scale
        android:fromXScale="1.0"
        android:fromYScale="1.0"
        android:toXScale="0.9"
        android:toYScale="0.9"
        android:pivotX="50%"
        android:pivotY="50%"
        android:duration="260"
        android:interpolator="@android:anim/accelerate_interpolator" />

    <!-- Fade out -->
    <alpha
        android:fromAlpha="1.0"
        android:toAlpha="0.0"
        android:duration="200" />
</set>
``

## File: app\src\main\res\anim\fragment_cursel_out_pop.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:shareInterpolator="false">

    <!-- Slide out to right with acceleration -->
    <translate
        android:fromXDelta="0%"
        android:toXDelta="12%"
        android:duration="260"
        android:interpolator="@android:anim/accelerate_interpolator" />

    <!-- Scale down slightly -->
    <scale
        android:fromXScale="1.0"
        android:fromYScale="1.0"
        android:toXScale="0.9"
        android:toYScale="0.9"
        android:pivotX="50%"
        android:pivotY="50%"
        android:duration="260"
        android:interpolator="@android:anim/accelerate_interpolator" />

    <!-- Fade out -->
    <alpha
        android:fromAlpha="1.0"
        android:toAlpha="0.0"
        android:duration="200" />
</set>
``

## File: app\src\main\res\color\bottom_nav_item_color.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Selected/Active state -->
    <item android:color="?attr/colorOnSecondaryContainer" android:state_checked="true"/>
    <!-- Unselected/Inactive state -->
    <item android:color="?attr/colorOnSurfaceVariant"/>
</selector>
``

## File: app\src\main\res\color\bottom_nav_ripple_color.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:alpha="0.12" android:color="?attr/colorOnSurface"/>
</selector>
``

## File: app\src\main\res\drawable\balance_track_bg.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorOutlineVariant" />
    <corners android:radius="6dp" />
</shape>
``

## File: app\src\main\res\drawable\default_album_art.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="280dp"
    android:height="280dp"
    android:viewportWidth="280"
    android:viewportHeight="280">

    <path
        android:fillColor="?attr/colorSurfaceVariant"
        android:pathData="M0,0h280v280h-280z"/>

    <group
        android:scaleX="8.0"
        android:scaleY="8.0"
        android:translateX="45.0"
        android:translateY="45.8">
        <path
            android:fillColor="?attr/colorOnSurface"
            android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/>
    </group>
</vector>
``

## File: app\src\main\res\drawable\eq_track_bg.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorOutlineVariant" />
    <corners android:radius="4dp" />
</shape>
``

## File: app\src\main\res\drawable\icon_effect_repeatedly.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="24dp" android:tint="#000000" android:viewportHeight="24" android:viewportWidth="24" android:width="24dp">
      
    <path android:fillColor="@android:color/white" android:pathData="M7,2v11h3v9l7,-12h-4l4,-8z"/>
    
</vector>
``

## File: app\src\main\res\drawable\icon_effect_slow.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:autoMirrored="true" android:height="24dp" android:tint="#000000" android:viewportHeight="24" android:viewportWidth="24" android:width="24dp">
      
    <path android:fillColor="@android:color/white" android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"/>
    
</vector>
``

## File: app\src\main\res\drawable\icon_effect_time.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="24dp" android:tint="#000000" android:viewportHeight="24" android:viewportWidth="24" android:width="24dp">
      
    <path android:fillColor="@android:color/white" android:pathData="M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM15.29,16.71L11,12.41V7h2v4.59l3.71,3.71L15.29,16.71z"/>
    
</vector>
``

## File: app\src\main\res\drawable\ic_8d_off.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:pathData="M13,15H17C17.283,15 17.521,14.904 17.712,14.712C17.904,14.521 18,14.283 18,14V10C18,9.717 17.904,9.479 17.712,9.288C17.521,9.096 17.283,9 17,9H13V15ZM14.5,13.5V10.5H16.5V13.5H14.5ZM4,20C3.45,20 2.979,19.804 2.588,19.413C2.196,19.021 2,18.55 2,18V6C2,5.45 2.196,4.979 2.588,4.588C2.979,4.196 3.45,4 4,4H20C20.55,4 21.021,4.196 21.413,4.588C21.804,4.979 22,5.45 22,6V18C22,18.55 21.804,19.021 21.413,19.413C21.021,19.804 20.55,20 20,20H4ZM4,18H20V6H4V18Z"
      android:fillColor="#ffffff"/>
  <path
      android:pathData="M9.333,15H7.667C7.208,15 6.816,14.882 6.49,14.648C6.163,14.413 6,14.13 6,13.8V12.9C6,12.65 6.122,12.438 6.365,12.262C6.608,12.087 6.903,12 7.25,12C6.903,12 6.608,11.913 6.365,11.738C6.122,11.563 6,11.35 6,11.1V10.2C6,9.87 6.163,9.587 6.49,9.352C6.816,9.118 7.208,9 7.667,9H9.333C9.792,9 10.184,9.118 10.51,9.352C10.837,9.587 11,9.87 11,10.2V11.1C11,11.35 10.878,11.563 10.635,11.738C10.392,11.913 10.097,12 9.75,12C10.097,12 10.392,12.087 10.635,12.262C10.878,12.438 11,12.65 11,12.9V13.8C11,14.13 10.837,14.413 10.51,14.648C10.184,14.882 9.792,15 9.333,15ZM7.667,10.2H9.333V11.4H7.667V10.2ZM7.667,13.8V12.6H9.333V13.8H7.667Z"
      android:fillColor="#ffffff"
      android:fillType="evenOdd"/>
  <path
      android:pathData="M3.081,2l18.019,18.743l-1.081,1.04l-18.019,-18.743z"
      android:fillColor="#ffffff"/>
</vector>
``

## File: app\src\main\res\drawable\ic_8d_on.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:pathData="M13,15H17C17.283,15 17.521,14.904 17.712,14.712C17.904,14.521 18,14.283 18,14V10C18,9.717 17.904,9.479 17.712,9.288C17.521,9.096 17.283,9 17,9H13V15ZM14.5,13.5V10.5H16.5V13.5H14.5ZM4,20C3.45,20 2.979,19.804 2.588,19.413C2.196,19.021 2,18.55 2,18V6C2,5.45 2.196,4.979 2.588,4.588C2.979,4.196 3.45,4 4,4H20C20.55,4 21.021,4.196 21.413,4.588C21.804,4.979 22,5.45 22,6V18C22,18.55 21.804,19.021 21.413,19.413C21.021,19.804 20.55,20 20,20H4ZM4,18H20V6H4V18Z"
      android:fillColor="#ffffff"/>
  <path
      android:pathData="M9.333,15H7.667C7.208,15 6.816,14.882 6.49,14.648C6.163,14.413 6,14.13 6,13.8V12.9C6,12.65 6.122,12.438 6.365,12.262C6.608,12.087 6.903,12 7.25,12C6.903,12 6.608,11.913 6.365,11.738C6.122,11.563 6,11.35 6,11.1V10.2C6,9.87 6.163,9.587 6.49,9.352C6.816,9.118 7.208,9 7.667,9H9.333C9.792,9 10.184,9.118 10.51,9.352C10.837,9.587 11,9.87 11,10.2V11.1C11,11.35 10.878,11.563 10.635,11.738C10.392,11.913 10.097,12 9.75,12C10.097,12 10.392,12.087 10.635,12.262C10.878,12.438 11,12.65 11,12.9V13.8C11,14.13 10.837,14.413 10.51,14.648C10.184,14.882 9.792,15 9.333,15ZM7.667,10.2H9.333V11.4H7.667V10.2ZM7.667,13.8V12.6H9.333V13.8H7.667Z"
      android:fillColor="#ffffff"
      android:fillType="evenOdd"/>
</vector>
``

## File: app\src\main\res\drawable\ic_check.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorOnPrimary"
        android:pathData="M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_chevron_right.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M544.08,479.77 L354.12,289.04l21.76,-21.89 211.85,212.62 -211.85,211.85 -21.76,-21.89 189.96,-189.96Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_close.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="m249,753 l-42,-42 231,-231 -231,-231 42,-42 231,231 231,-231 42,42 -231,231 231,231 -42,42 -231,-231 -231,231Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_equalizer.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M10,20h4L14,4h-4v16zM4,20h4v-8L4,12v8zM16,9v11h4L20,9h-4z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_forward_30.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M281,650v-50h121v-56h-82v-49h82v-57L281,438v-49h127q18.7,0 31.35,12.65Q452,414.3 452,433v173q0,18.7 -12.65,31.35Q426.7,650 408,650L281,650ZM553,650q-18.7,0 -31.35,-12.65Q509,624.7 509,606v-173q0,-18.7 12.65,-31.35Q534.3,389 553,389h83q18.7,0 31.35,12.65Q680,414.3 680,433v173q0,18.7 -12.65,31.35Q654.7,650 636,650h-83ZM559,600h71v-162h-71v162ZM480,880q-75,0 -140.5,-28T225,775q-49,-49 -77,-114.5T120,520q0,-75 28,-140.5T225,265q49,-49 114.5,-77T480,160h21l-78,-78 41,-41 147,147 -147,147 -41,-41 74,-74h-17q-125.36,0 -212.68,87.32Q180,394.64 180,520q0,125.36 87.32,212.68Q354.64,820 480,820q125.36,0 212.68,-87.32Q780,645.36 780,520h60q0,75 -28,140.5T735,775q-49,49 -114.5,77T480,880Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_fullscreen.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M120,840v-193h60v133h133v60L120,840ZM647,840v-60h133v-133h60v193L647,840ZM120,313v-193h193v60L180,180v133h-60ZM780,313v-133L647,180v-60h193v193h-60Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_github.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorOnSurface"
        android:pathData="M12,2C6.477,2 2,6.477 2,12c0,4.42 2.865,8.17 6.839,9.49 0.5,0.092 0.682,-0.217 0.682,-0.482 0,-0.237 -0.009,-0.866 -0.014,-1.699 -2.782,0.604 -3.369,-1.34 -3.369,-1.34 -0.454,-1.156 -1.11,-1.464 -1.11,-1.464 -0.908,-0.62 0.069,-0.608 0.069,-0.608 1.003,0.071 1.531,1.03 1.531,1.03 0.891,1.529 2.341,1.087 2.91,0.831 0.091,-0.646 0.349,-1.086 0.635,-1.336 -2.22,-0.253 -4.555,-1.11 -4.555,-4.943 0,-1.091 0.39,-1.984 1.029,-2.683 -0.103,-0.253 -0.446,-1.27 0.098,-2.647 0,0 0.84,-0.269 2.75,1.025A9.578,9.578 0,0 1,12 6.836c0.85,0.004 1.705,0.114 2.504,0.336 1.909,-1.294 2.747,-1.025 2.747,-1.025 0.546,1.377 0.203,2.394 0.1,2.647 0.64,0.699 1.026,1.592 1.026,2.683 0,3.842 -2.339,4.687 -4.566,4.935 0.359,0.309 0.678,0.919 0.678,1.852 0,1.336 -0.012,2.415 -0.012,2.743 0,0.267 0.18,0.579 0.688,0.481C19.138,20.167 22,16.418 22,12c0,-5.523 -4.477,-10 -10,-10z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_instagram.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorOnSurface"
        android:pathData="M12,2.163c3.204,0 3.584,0.012 4.85,0.07 3.252,0.148 4.771,1.691 4.919,4.919 0.058,1.265 0.069,1.645 0.069,4.849 0,3.205 -0.012,3.584 -0.069,4.849 -0.149,3.225 -1.664,4.771 -4.919,4.919 -1.266,0.058 -1.644,0.07 -4.85,0.07 -3.204,0 -3.584,-0.012 -4.849,-0.07 -3.26,-0.149 -4.771,-1.699 -4.919,-4.92 -0.058,-1.265 -0.07,-1.644 -0.07,-4.849 0,-3.204 0.013,-3.583 0.07,-4.849 0.149,-3.227 1.664,-4.771 4.919,-4.919 1.266,-0.057 1.645,-0.069 4.849,-0.069zM12,0C8.741,0 8.333,0.014 7.053,0.072 2.695,0.272 0.273,2.69 0.073,7.052 0.014,8.333 0,8.741 0,12c0,3.259 0.014,3.668 0.072,4.948 0.2,4.358 2.618,6.78 6.98,6.98C8.333,23.986 8.741,24 12,24c3.259,0 3.668,-0.014 4.948,-0.072 4.354,-0.2 6.782,-2.618 6.979,-6.98 0.059,-1.28 0.073,-1.689 0.073,-4.948 0,-3.259 -0.014,-3.667 -0.072,-4.947 -0.196,-4.354 -2.617,-6.78 -6.979,-6.98C15.668,0.014 15.259,0 12,0zM12,5.838c-3.403,0 -6.162,2.759 -6.162,6.162S8.597,18.163 12,18.163s6.162,-2.759 6.162,-6.163c0,-3.403 -2.759,-6.162 -6.162,-6.162zM12,16c-2.209,0 -4,-1.79 -4,-4 0,-2.209 1.791,-4 4,-4s4,1.791 4,4c0,2.21 -1.791,4 -4,4zM18.406,4.155c-0.796,0 -1.441,0.645 -1.441,1.44s0.645,1.44 1.441,1.44c0.795,0 1.439,-0.645 1.439,-1.44s-0.644,-1.44 -1.439,-1.44z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_launcher_background.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="520"
    android:viewportHeight="520">
  <group android:scaleX="0.75"
      android:scaleY="0.75"
      android:translateX="65"
      android:translateY="65">
    <path
        android:pathData="M4,0h512v512h-512z">
      <aapt:attr name="android:fillColor">
        <gradient
            android:startX="516"
            android:startY="512"
            android:endX="20"
            android:endY="21"
            android:type="linear">
          <item android:offset="0.59" android:color="#FF000000"/>
          <item android:offset="0.99" android:color="#FF474747"/>
        </gradient>
      </aapt:attr>
    </path>
  </group>
</vector>
``

## File: app\src\main\res\drawable\ic_launcher_foreground.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="377"
    android:viewportHeight="377">
  <group android:scaleX="0.45833334"
      android:scaleY="0.45833334"
      android:translateX="102.104164"
      android:translateY="102.104164">
    <path
        android:pathData="M292.07,376.52L334.69,376.52C346.19,376.52 356.04,372.43 364.23,364.23C372.43,356.04 376.52,346.19 376.52,334.69V188.26C376.52,162.11 371.55,137.62 361.62,114.79C351.68,91.95 338.26,72.08 321.35,55.17C304.44,38.26 284.57,24.84 261.74,14.9C238.9,4.97 214.41,0 188.26,0C162.11,0 137.62,4.97 114.79,14.9C91.95,24.84 72.08,38.26 55.17,55.17C38.26,72.08 24.84,91.95 14.9,114.79C4.97,137.62 0,162.11 0,188.26V334.69C0,346.19 4.1,356.04 12.29,364.23C20.48,372.43 30.33,376.52 41.84,376.52H84.46L114.79,292.85L84.46,209.18L41.84,209.18V188.26C41.84,147.47 56.04,112.87 84.46,84.46C112.87,56.04 147.47,41.84 188.26,41.84C229.05,41.84 263.65,56.04 292.07,84.46C320.48,112.87 334.69,147.47 334.69,188.26V209.18H292.07L261.74,292.85L292.07,376.52Z"
        android:fillColor="#ffffff"/>
    <path
        android:pathData="M195.75,343.32V243.38H181.48V343.32H195.75ZM224.31,321.91V264.8H210.03V321.91H224.31ZM167.2,321.91V264.8H152.92V321.91H167.2ZM252.86,300.49V286.21H238.58V300.49H252.86ZM138.64,300.49V286.21H124.37V300.49H138.64Z"
        android:fillColor="#ffffff"/>
  </group>
</vector>
``

## File: app\src\main\res\drawable\ic_menu_search.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="m777.65,816.11 l-247.89,-248q-29.61,24.77 -68.3,38.22 -38.69,13.44 -79.23,13.44 -100.45,0 -169.78,-69.36t-69.33,-169.5q0,-100.14 69.25,-169.6 69.26,-69.46 169.5,-69.46t169.71,69.42q69.46,69.43 69.46,169.67 0,41.91 -14.08,80.75 -14.08,38.85 -37.58,67.28l248,247.41 -39.73,39.73ZM382.08,563.81q76.8,0 129.9,-53.02 53.1,-53.03 53.1,-130 0,-76.98 -53.1,-129.98 -53.1,-53 -130,-53t-129.9,53.02q-53,53.02 -53,130 0,76.98 53.01,129.98 53.02,53 129.99,53Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_music_note.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_pause.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_pip.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M80,440v-60h178L57,180l43,-43 200,200v-177h60v280L80,440ZM140,800q-24,0 -42,-18t-18,-42v-230h60v230h340v60L140,800ZM820,520v-300L430,220v-60h390q24,0 42,18t18,42v300h-60ZM540,800v-220h340v220L540,800Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_play.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M8,5v14l11,-7z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_replay_30.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M480,880q-75,0 -140.5,-28T225,775q-49,-49 -77,-114.5T120,520h60q0,125 87.32,212.5T480,820q125.36,0 212.68,-87.32Q780,645.36 780,520q0,-125.36 -85,-212.68Q610,220 485,220h-22l73,73 -42,42 -147,-147 147,-147 41,41 -78,78h23q75,0 140.5,28T735,265q49,49 77,114.5T840,520q0,75 -28,140.5T735,775q-49,49 -114.5,77T480,880ZM281,650v-50h121v-55h-82v-50h82v-56L281,439v-50h127q18.7,0 31.35,12.65Q452,414.3 452,433v173q0,18.7 -12.65,31.35Q426.7,650 408,650L281,650ZM553,650q-18.7,0 -31.35,-12.65Q509,624.7 509,606v-173q0,-18.7 12.65,-31.35Q534.3,389 553,389h83q18.7,0 31.35,12.65Q680,414.3 680,433v173q0,18.7 -12.65,31.35Q654.7,650 636,650h-83ZM559,600h71v-161h-71v161Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_save.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M426,128v499L204,405l-76,76 351,351 353,-353 -76,-76 -224,224v-499L426,128Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_settings.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnSurfaceVariant">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98s-0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.39,-0.3 -0.61,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65c-0.03,-0.22 -0.22,-0.38 -0.44,-0.38h-4c-0.22,0 -0.41,0.16 -0.42,0.38l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.22,-0.09 -0.49,-0.01 -0.61,0.22l-2,3.46c-0.12,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98s0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.39,0.3 0.61,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.22 0.22,0.38 0.44,0.38h4c0.22,0 0.41,-0.16 0.42,-0.38l0.38,-2.65c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.22,0.09 0.49,0.01 0.61,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM12,15.5c-1.93,0 -3.5,-1.57 -3.5,-3.5s1.57,-3.5 3.5,-3.5 3.5,1.57 3.5,3.5 -1.57,3.5 -3.5,3.5z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_stop.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M6,6h12v12H6z"/>
</vector>
``

## File: app\src\main\res\drawable\ic_update.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M427,836q-64,-13 -121,-46t-98.5,-79.5Q166,664 142.5,605T119,480q0,-79 32,-148.5T253,205h-96v-54h190v190h-54v-100q-63,48 -91.5,110T173,480q0,107 69,191.5T427,782v54ZM423,646L272,496l38,-38 113,113 227,-227 38,37 -265,265ZM613,810v-190h54v100q62,-48 91,-110.5T787,480q0,-107 -69,-191.5T533,178v-54q137,27 222.5,125.5T841,480q0,79 -32,149T707,756h96v54L613,810Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_vibration.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M360,880q-10,0 -18,-5.5T331,859L228,510L80,510v-60h170q10,0 18,5.5t11,15.5l76,256 136,-623q2,-11 10,-17.5t19,-6.5q11,0 19,6t10,17l94,423 68,-225q3,-10 11,-15.5t18,-5.5q10,0 17.5,5t10.5,14l54,151h58v60h-80q-10,0 -17.5,-5.5T772,490l-30,-83 -74,252q-3,10 -11,16t-18,5q-10,-1 -18,-7t-10,-16l-91,-402 -131,602q-2,10 -10,16t-19,7Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_vibration_off.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M833,919 L537,623q-22,18 -37.5,32.5T473,686q-11,16 -20.5,35T433,766q-20,53 -63,83t-98,30q-60,0 -103,-41t-48,-102h60q5,36 31,59.5t60,23.5q36,0 63.5,-22.5T382,729q11,-26 23,-47.5t25.5,-39.5q13.5,-18 29,-33.5T493,579L193,279q-5,15 -8.5,31.5T180,344h-60q2,-30 8.5,-58t18.5,-53L26,112l43,-43L876,876l-43,43ZM732,646l-43,-43q45,-53 68,-117.5T780,352q0,-77 -26.5,-145.5T676,87l45,-40q57,59 88,138t31,167q0,81 -27.5,156T732,646ZM622,536l-46,-46q17,-29 25.5,-61.5T610,356q0,-90 -61,-151.5T398,143q-37,0 -69.5,10T268,182l-43,-43q36,-27 80,-41.5t93,-14.5q120,0 196,76.5T670,356q0,56 -11.5,99.5T622,536ZM482,396 L357,271q10,-5 20,-7.5t21,-2.5q39,0 66,28t27,67q0,11 -2,21t-7,19ZM396,449q-39,0 -66,-27t-27,-66q0,-11 2.5,-22t7.5,-21l126,126q-10,5 -21,7.5t-22,2.5Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_volume_down.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M173,622v-285h172.5l232,-231v748l-232,-232L173,622ZM635,655v-350.5q59,19 95.5,67.75T767,480q0,60 -36.5,107.25T635,655Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_volume_off.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="m817.5,922.5 l-135,-136q-24,16 -51.75,28.25T572.5,833.5L572.5,761q14.5,-4.5 30.5,-10t29.5,-16.5L495,594v260L264,622L91.5,622v-285L244,337L29.5,117.5 78,69 867,872l-49.5,50.5ZM797,677l-49.5,-51.5q20,-32 29.75,-69.25T787,479q0,-100.5 -59.75,-179.75T572.5,197v-72.5q124,29 204,128.25t80,226.25q0,53.5 -15.5,103.75T797,677ZM670.5,548.5l-98,-100v-137q49.5,23.5 81.25,68.75T685.5,480q0,18 -4,35.5t-11,33ZM495,369 L365,236l130,-130v263Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_volume_up.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:pathData="M579.5,833.5v-72.7q93.5,-28.3 154,-105.55T794,479q0,-98.5 -60,-176.25T579.5,197.2v-72.7q124.73,28.75 203.86,128.12Q862.5,352 862.5,479q0,127.5 -79.14,226.88 -79.13,99.37 -203.86,127.62ZM97.5,622v-285L270,337l232,-231v748L270,622L97.5,622ZM559.5,655v-350.5q58,20 95,67.08t37,108.49q0,60.93 -37.5,108.43 -37.5,47.5 -94.5,66.5Z"
      android:fillColor="#FFFFFF"/>
</vector>
``

## File: app\src\main\res\drawable\ic_youtube.xml

``xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="800dp"
    android:height="800dp"
    android:viewportWidth="20"
    android:viewportHeight="20">
  <path
      android:pathData="M7.988,12.586L7.988,6.974C9.981,7.912 11.524,8.817 13.348,9.793C11.843,10.628 9.981,11.564 7.988,12.586M19.091,4.183C18.747,3.73 18.162,3.378 17.538,3.261C15.705,2.914 4.271,2.913 2.439,3.261C1.939,3.355 1.494,3.582 1.111,3.934C-0.5,5.43 0.005,13.452 0.393,14.751C0.557,15.313 0.768,15.719 1.034,15.985C1.376,16.337 1.845,16.58 2.384,16.688C3.893,17.001 11.668,17.175 17.506,16.735C18.044,16.642 18.52,16.392 18.896,16.024C20.386,14.535 20.284,6.062 19.091,4.183"
      android:strokeWidth="1"
      android:fillColor="#000000"
      android:fillType="evenOdd"
      android:strokeColor="#00000000"/>
</vector>
``

## File: app\src\main\res\drawable\shape_album_art_round.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">

    <solid android:color="?attr/colorSurfaceContainerHigh" />

    <corners android:radius="12dp" />

</shape>
``

## File: app\src\main\res\drawable\tab_icon_active.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/colorPrimary" />
</selector>
``

## File: app\src\main\res\drawable\tab_icon_color.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/colorPrimary" android:state_selected="true" />
    <item android:color="?attr/colorOnSurfaceVariant" />
</selector>
``

## File: app\src\main\res\drawable\tab_icon_inactive.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/colorOnSurfaceVariant" />
</selector>
``

## File: app\src\main\res\drawable\tab_indicator_vertical.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/colorPrimary" />
    <size
        android:width="4dp"
        android:height="48dp" />
    <corners android:radius="2dp" />
</shape>
``

## File: app\src\main\res\layout\activity_main.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface">

    <fragment
        android:id="@+id/nav_host_fragment_activity_main"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/nav_view"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:navGraph="@navigation/nav_graph" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/nav_view"
        style="@style/Widget.Material3.BottomNavigationView"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:backgroundTint="?attr/colorSurfaceContainer"
        app:itemIconTint="@color/bottom_nav_item_color"
        app:itemTextColor="@color/bottom_nav_item_color"
        app:itemRippleColor="@color/bottom_nav_ripple_color"
        app:itemActiveIndicatorStyle="@style/BottomNavActiveIndicator"
        app:labelVisibilityMode="labeled"
        app:menu="@menu/bottom_nav_menu"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\layout\bottom_sheet_song_list.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<!-- We wrap in a CardView to easily force top-only corner rounding -->
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardCornerRadius="32dp"
    app:cardElevation="0dp"
    app:strokeWidth="0dp"
    app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.BottomSheet"
    android:backgroundTint="?attr/colorSurfaceContainerLow"
    app:layout_behavior="com.google.android.material.bottomsheet.BottomSheetBehavior">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <!-- Standard M3 Drag Handle -->
        <com.google.android.material.bottomsheet.BottomSheetDragHandleView
            android:id="@+id/drag_handle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/tvTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Load Song"
            android:textAppearance="?attr/textAppearanceTitleLarge"
            android:textColor="?attr/colorOnSurface"
            android:textStyle="bold"
            android:gravity="center"
            android:paddingHorizontal="24dp"
            android:paddingBottom="8dp" />

        <!-- Search Bar with Pill Shape -->
        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/searchLayout"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox.Dense"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="20dp"
            android:layout_marginTop="8dp"
            android:hint="Search your library..."
            app:startIconDrawable="@android:drawable/ic_menu_search"
            app:endIconMode="clear_text"
            app:boxCornerRadiusTopStart="28dp"
            app:boxCornerRadiusTopEnd="28dp"
            app:boxCornerRadiusBottomStart="28dp"
            app:boxCornerRadiusBottomEnd="28dp"
            app:boxStrokeWidth="0dp"
            app:boxStrokeWidthFocused="1dp"
            app:boxBackgroundColor="?attr/colorSurfaceContainerHigh">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etSearch"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="text"
                android:maxLines="1"
                android:paddingVertical="14dp" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- Horizontal Filter Chips -->
        <HorizontalScrollView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:scrollbars="none"
            android:clipToPadding="false"
            android:paddingHorizontal="20dp"
            android:paddingTop="16dp"
            android:paddingBottom="12dp">

            <com.google.android.material.chip.ChipGroup
                android:id="@+id/chipGroupSort"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:singleSelection="true"
                app:selectionRequired="true">

                <com.google.android.material.chip.Chip
                    android:id="@+id/chipName"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="A-Z"
                    app:chipCornerRadius="12dp"
                    android:checked="true" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chipDate"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:chipCornerRadius="12dp"
                    android:text="Recently Added" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chipArtist"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:chipCornerRadius="12dp"
                    android:text="Artist" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chipSize"
                    style="@style/Widget.Material3.Chip.Filter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:chipCornerRadius="12dp"
                    android:text="Size" />

            </com.google.android.material.chip.ChipGroup>
        </HorizontalScrollView>

        <com.google.android.material.divider.MaterialDivider
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:alpha="0.5" />

        <!-- Song List -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvSongs"
            android:layout_width="match_parent"
            android:layout_height="420dp"
            android:clipToPadding="false"
            android:paddingVertical="4dp"
            tools:listitem="@android:layout/simple_list_item_2" />

        <!-- Footer Actions -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingHorizontal="20dp"
            android:paddingTop="12dp"
            android:paddingBottom="24dp">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCancel"
                style="@style/Widget.Material3.Button.TonalButton"
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:text="Close"
                app:cornerRadius="28dp" />
        </LinearLayout>

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
``

## File: app\src\main\res\layout\fragment_effects.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <!-- Header -->
        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvHeader"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/audio_effects"
            android:textAppearance="?attr/textAppearanceHeadlineMedium"
            android:textColor="?attr/colorOnSurface" />

        <!-- Processing Status (MOVED TO TOP) -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardProcessing"
            style="?attr/materialCardViewFilledStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorPrimaryContainer"
            app:cardCornerRadius="16dp"
            app:cardElevation="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp"
                android:orientation="vertical">

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvProcessingStatus"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/processing_audio"
                    android:textAppearance="?attr/textAppearanceBodyLarge"
                    android:textColor="?attr/colorOnPrimaryContainer"
                    android:fontFamily="sans-serif-medium"
                    android:gravity="center" />

                <com.google.android.material.progressindicator.LinearProgressIndicator
                    android:id="@+id/progressBar"
                    style="@style/Widget.Material3Expressive.LinearProgressIndicator.Wavy"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:indeterminate="false"
                    app:indicatorColor="?attr/colorPrimary"
                    app:trackColor="?attr/colorSurfaceVariant"
                    app:trackThickness="8dp"
                    app:trackCornerRadius="4dp"
                    app:wavelength="80dp"
                    app:waveAmplitude="4dp"
                    app:waveSpeed="25dp"
                    app:showAnimationBehavior="inward" />


            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- GROUP 1: (8D + Bass + 5-Band Equalizer) -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardGroup1"
            style="?attr/materialCardViewFilledStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            app:cardBackgroundColor="?attr/colorSurfaceVariant"
            app:cardCornerRadius="20dp"
            app:cardElevation="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <!-- 8D Section -->
                <LinearLayout
                    android:id="@+id/section8d"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tv8DTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="@string/eight_d_audio"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- compact toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switch8D"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <com.google.android.material.textview.MaterialTextView
                        android:id="@+id/tv8DDescription"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="@string/eight_d_description"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />
                </LinearLayout>

                <!-- Divider -->
                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="?attr/colorSurface" />

                <!-- Bass Section -->
                <LinearLayout
                    android:id="@+id/sectionBass"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBassTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="@string/bass_boost"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- compact toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switchBass"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="12dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBassBoostLabel"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/bass_level"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBassBoostValue"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelLarge"
                            android:textColor="?attr/colorPrimary" />
                    </LinearLayout>

                    <com.google.android.material.slider.Slider
                        android:id="@+id/sliderBassBoost"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:valueFrom="-15"
                        android:valueTo="15"
                        android:value="0"
                        android:stepSize="1"
                        android:enabled="false"
                        app:labelBehavior="gone"
                        app:trackCornerSize="8dp"
                        app:thumbColor="?attr/colorPrimary"
                        app:trackColorActive="?attr/colorPrimary"
                        app:trackColorInactive="?attr/colorOutlineVariant"
                        app:haloColor="?attr/colorPrimaryContainer"
                        app:trackHeight="20dp"
                        app:thumbRadius="10dp" />
                </LinearLayout>

                <!-- Divider -->
                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="?attr/colorSurface" />

                <!-- 5-BAND EQUALIZER -->
                <LinearLayout
                    android:id="@+id/sectionEqualizer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvEqualizerTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="5-Band Equalizer"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- compact toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switchEqualizer"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <!-- Band 1 -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="16dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand1Label"
                            android:layout_width="64dp"
                            android:layout_height="wrap_content"
                            android:text="60 Hz"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBand1"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:valueFrom="-12"
                            android:valueTo="12"
                            android:value="0"
                            app:trackCornerSize="3dp"
                            android:enabled="false"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:trackColorActive="?attr/colorPrimary"
                            app:trackColorInactive="?attr/colorOutlineVariant"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="10dp"
                            app:thumbRadius="8dp" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand1Value"
                            android:layout_width="56dp"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelMedium"
                            android:textColor="?attr/colorPrimary"
                            android:gravity="end" />
                    </LinearLayout>

                    <!-- Band 2 -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="12dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand2Label"
                            android:layout_width="64dp"
                            android:layout_height="wrap_content"
                            android:text="230 Hz"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBand2"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:valueFrom="-12"
                            android:valueTo="12"
                            android:value="0"
                            app:trackCornerSize="3dp"
                            android:enabled="false"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:trackColorActive="?attr/colorPrimary"
                            app:trackColorInactive="?attr/colorOutlineVariant"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="10dp"
                            app:thumbRadius="8dp" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand2Value"
                            android:layout_width="56dp"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelMedium"
                            android:textColor="?attr/colorPrimary"
                            android:gravity="end" />
                    </LinearLayout>

                    <!-- Band 3 -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="12dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand3Label"
                            android:layout_width="64dp"
                            android:layout_height="wrap_content"
                            android:text="910 Hz"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBand3"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:valueFrom="-12"
                            android:valueTo="12"
                            android:value="0"
                            app:trackCornerSize="3dp"
                            android:enabled="false"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:trackColorActive="?attr/colorPrimary"
                            app:trackColorInactive="?attr/colorOutlineVariant"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="10dp"
                            app:thumbRadius="8dp" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand3Value"
                            android:layout_width="56dp"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelMedium"
                            android:textColor="?attr/colorPrimary"
                            android:gravity="end" />
                    </LinearLayout>

                    <!-- Band 4 -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="12dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand4Label"
                            android:layout_width="64dp"
                            android:layout_height="wrap_content"
                            android:text="3.6 kHz"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBand4"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:valueFrom="-12"
                            android:valueTo="12"
                            android:value="0"
                            app:trackCornerSize="3dp"
                            android:enabled="false"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:trackColorActive="?attr/colorPrimary"
                            app:trackColorInactive="?attr/colorOutlineVariant"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="10dp"
                            app:thumbRadius="8dp" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand4Value"
                            android:layout_width="56dp"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelMedium"
                            android:textColor="?attr/colorPrimary"
                            android:gravity="end" />
                    </LinearLayout>

                    <!-- Band 5 -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="12dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand5Label"
                            android:layout_width="64dp"
                            android:layout_height="wrap_content"
                            android:text="14 kHz"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBand5"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:valueFrom="-12"
                            android:valueTo="12"
                            android:value="0"
                            app:trackCornerSize="3dp"
                            android:enabled="false"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:trackColorActive="?attr/colorPrimary"
                            app:trackColorInactive="?attr/colorOutlineVariant"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="10dp"
                            app:thumbRadius="8dp" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBand5Value"
                            android:layout_width="56dp"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelMedium"
                            android:textColor="?attr/colorPrimary"
                            android:gravity="end" />
                    </LinearLayout>
                </LinearLayout>

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- GROUP 2: (Loudness + Balance + Playback Speed) -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardGroup2"
            style="?attr/materialCardViewFilledStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="24dp"
            app:cardBackgroundColor="?attr/colorSurfaceVariant"
            app:cardCornerRadius="20dp"
            app:cardElevation="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <!-- Loudness Section -->
                <LinearLayout
                    android:id="@+id/sectionLoudness"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvLoudnessTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="Loudness Enhancer"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- compact toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switchLoudness"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="16dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvLoudnessLabel"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="Gain"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvLoudnessValue"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="+0 dB"
                            android:textAppearance="?attr/textAppearanceLabelLarge"
                            android:textColor="?attr/colorPrimary" />
                    </LinearLayout>

                    <com.google.android.material.slider.Slider
                        android:id="@+id/sliderLoudness"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:valueFrom="0"
                        android:valueTo="12"
                        android:value="0"
                        android:stepSize="1"
                        android:enabled="false"
                        app:labelBehavior="gone"
                        app:trackCornerSize="4dp"
                        app:thumbColor="?attr/colorPrimary"
                        app:trackColorActive="?attr/colorPrimary"
                        app:trackColorInactive="?attr/colorOutlineVariant"
                        app:haloColor="?attr/colorPrimaryContainer"
                        app:trackHeight="12dp"
                        app:thumbRadius="10dp" />
                </LinearLayout>

                <!-- Divider -->
                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="?attr/colorSurface" />

                <!-- Balance Section -->
                <LinearLayout
                    android:id="@+id/sectionBalance"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBalanceTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="Stereo Balance"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- compact toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switchBalance"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="16dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBalanceLabel"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="Position"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvBalanceValue"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Center"
                            android:textAppearance="?attr/textAppearanceLabelLarge"
                            android:textColor="?attr/colorPrimary" />
                    </LinearLayout>

                    <RelativeLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp">
                        <View
                            android:id="@+id/balanceCenterLine"
                            android:layout_width="3dp"
                            android:layout_height="32dp"
                            android:layout_centerHorizontal="true"
                            android:layout_centerVertical="true"
                            android:background="?attr/colorPrimary" />

                        <com.google.android.material.slider.Slider
                            android:id="@+id/sliderBalance"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:valueFrom="-50"
                            android:valueTo="50"
                            android:value="0"
                            app:trackCornerSize="6dp"
                            app:labelBehavior="gone"
                            app:thumbColor="?attr/colorPrimary"
                            app:haloColor="?attr/colorPrimaryContainer"
                            app:trackHeight="15dp"
                            app:thumbRadius="10dp" />
                    </RelativeLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginTop="4dp"
                        android:paddingStart="20dp"
                        android:paddingEnd="20dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Left"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorOnSurfaceVariant"
                            android:textStyle="bold" />

                        <View
                            android:layout_width="0dp"
                            android:layout_height="0dp"
                            android:layout_weight="1" />

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Center"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorPrimary"
                            android:textStyle="bold" />

                        <View
                            android:layout_width="0dp"
                            android:layout_height="0dp"
                            android:layout_weight="1" />

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Right"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorOnSurfaceVariant"
                            android:textStyle="bold" />
                    </LinearLayout>
                </LinearLayout>

                <!-- Divider -->
                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="?attr/colorSurface" />

                <!-- Playback Speed Section -->
                <!-- Playback Speed Section - FIXED ALIGNMENT -->
                <LinearLayout
                    android:id="@+id/sectionSpeed"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <!-- Title and Toggle Row -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvSpeedTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:paddingTop="2dp"
                            android:text="Playback Speed"
                            android:textAppearance="?attr/textAppearanceTitleLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <!-- Properly aligned toggle -->
                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switchSpeed"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:checked="true"
                            app:thumbTint="?attr/colorPrimary"
                            app:trackTint="?attr/colorSurfaceVariant"
                            app:trackDecorationTint="?attr/colorOutlineVariant" />
                    </LinearLayout>

                    <com.google.android.material.textview.MaterialTextView
                        android:id="@+id/tvSpeedDescription"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="Adjust playback speed"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="16dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvSpeedLabel"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="Speed"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvSpeedValue"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="1.00x"
                            android:textAppearance="?attr/textAppearanceLabelLarge"
                            android:textColor="?attr/colorPrimary" />
                    </LinearLayout>

                    <com.google.android.material.slider.Slider
                        android:id="@+id/sliderSpeed"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:valueFrom="0.5"
                        android:valueTo="2.0"
                        android:value="1.0"
                        android:stepSize="0.05"
                        app:labelBehavior="gone"
                        app:thumbColor="?attr/colorPrimary"
                        app:trackColorActive="?attr/colorPrimary"
                        app:trackColorInactive="?attr/colorOutlineVariant"
                        app:haloColor="?attr/colorPrimaryContainer"
                        app:trackHeight="20dp"
                        app:thumbRadius="10dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginTop="8dp"
                        android:paddingStart="20dp"
                        android:paddingEnd="20dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="0.5x"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorOnSurfaceVariant" />

                        <View
                            android:layout_width="0dp"
                            android:layout_height="0dp"
                            android:layout_weight="1" />

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="1.0x"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorPrimary"
                            android:textStyle="bold" />

                        <View
                            android:layout_width="0dp"
                            android:layout_height="0dp"
                            android:layout_weight="1" />

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="2.0x"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorOnSurfaceVariant" />
                    </LinearLayout>
                </LinearLayout>

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>

</ScrollView>
``

## File: app\src\main\res\layout\fragment_player.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:scrollbars="none">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingStart="@dimen/margin_screen_horizontal"
            android:paddingEnd="@dimen/margin_screen_horizontal"
            android:paddingBottom="@dimen/margin_large">

            <!-- TOP SPACER -->
            <View
                android:id="@+id/topSpacer"
                android:layout_width="0dp"
                android:layout_height="@dimen/padding_top_content"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- ALBUM ART -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardAlbumArt"
                android:layout_width="0dp"
                android:layout_height="0dp"
                app:layout_constraintTop_toBottomOf="@id/topSpacer"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintDimensionRatio="1:1"
                app:cardCornerRadius="30dp"
                app:cardElevation="0dp"
                app:cardBackgroundColor="?attr/colorSurfaceVariant">

                <ImageView
                    android:id="@+id/ivAlbumArt"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:scaleType="centerCrop"
                    android:src="@drawable/ic_launcher_foreground"
                    android:contentDescription="Album Art"/>

            </com.google.android.material.card.MaterialCardView>

            <!-- SONG TITLE -->
            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvSongName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:text="@string/no_song_selected"
                android:textAppearance="?attr/textAppearanceTitleLarge"
                android:textColor="?attr/colorOnSurface"
                android:textStyle="bold"
                android:gravity="center"
                android:maxLines="2"
                android:ellipsize="marquee"
                android:marqueeRepeatLimit="marquee_forever"
                android:singleLine="true"
                android:focusable="true"
                android:focusableInTouchMode="true"
                app:layout_constraintTop_toBottomOf="@id/cardAlbumArt"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- SONG HAPTICS CHIP -->
            <com.google.android.material.chip.Chip
                android:id="@+id/chipSongHaptics"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:checkable="true"
                android:checked="false"
                android:text="@string/song_haptics"
                app:checkedIcon="@drawable/ic_vibration"
                app:chipIcon="@drawable/ic_vibration_off"
                app:chipIconSize="18dp"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toBottomOf="@id/tvSongName" />

            <!-- WAVY PROGRESS INDICATOR (REPLACES SEEKBAR) -->
            <com.google.android.material.progressindicator.LinearProgressIndicator
                android:id="@+id/waveProgress"
                style="@style/Widget.Material3Expressive.LinearProgressIndicator.Wavy"
                android:layout_width="0dp"
                android:layout_height="12dp"
                android:layout_marginTop="20dp"
                android:layout_marginBottom="8dp"
                android:max="1000"
                app:indicatorColor="?attr/colorPrimary"
                app:trackColor="?attr/colorSurfaceVariant"
                app:trackCornerRadius="2dp"
                app:trackThickness="6dp"
                app:wavelength="80dp"
                app:waveAmplitude="4dp"
                app:layout_constraintTop_toBottomOf="@id/chipSongHaptics"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>
            <!-- SEEKBAR (HIDDEN BUT FUNCTIONAL FOR SEEKING) -->
            <com.google.android.material.slider.Slider
                android:id="@+id/seekBar"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="0dp"
                android:valueFrom="0"
                android:valueTo="100"
                android:value="0"
                android:alpha="0"
                app:labelBehavior="gone"
                app:thumbRadius="8dp"
                app:trackHeight="8dp"
                app:layout_constraintTop_toTopOf="@id/waveProgress"
                app:layout_constraintBottom_toBottomOf="@id/waveProgress"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- TIME LABELS -->
            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvCurrentTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="00:00"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="?attr/colorOnSurfaceVariant"
                app:layout_constraintTop_toBottomOf="@id/waveProgress"
                app:layout_constraintStart_toStartOf="parent"/>

            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvTotalTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="00:00"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="?attr/colorOnSurfaceVariant"
                app:layout_constraintTop_toBottomOf="@id/waveProgress"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- PLAYER CONTROLS CARD -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/playerControlsCard"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="28dp"
                app:layout_constraintTop_toBottomOf="@id/tvTotalTime"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:cardCornerRadius="36dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp"
                app:cardBackgroundColor="?attr/colorSurfaceContainerHigh">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:paddingHorizontal="20dp"
                    android:paddingVertical="16dp"
                    android:gravity="center">

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center">

                        <!-- Skip 30s Back -->
                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRewind30"
                            style="@style/Widget.Material3.Button.IconButton.Filled.Tonal"
                            android:layout_width="64dp"
                            android:layout_height="64dp"
                            android:insetLeft="0dp"
                            android:insetTop="0dp"
                            android:insetRight="0dp"
                            android:insetBottom="0dp"
                            android:contentDescription="Rewind 30 seconds"
                            app:icon="@drawable/ic_replay_30"
                            app:iconSize="32dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnSecondaryContainer"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>

                        <!-- Play/Pause Button -->
                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnPlayPauseToggle"
                            style="@style/Widget.Material3.Button.IconButton.Filled"
                            android:layout_width="88dp"
                            android:layout_height="88dp"
                            android:layout_marginHorizontal="24dp"
                            android:insetLeft="0dp"
                            android:insetTop="0dp"
                            android:insetRight="0dp"
                            android:insetBottom="0dp"
                            android:contentDescription="Play Pause"
                            app:icon="@drawable/ic_pause"
                            app:iconSize="44dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnPrimary"
                            app:backgroundTint="?attr/colorPrimary"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>

                        <!-- Skip 30s Forward -->
                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnForward30"
                            style="@style/Widget.Material3.Button.IconButton.Filled.Tonal"
                            android:layout_width="64dp"
                            android:layout_height="64dp"
                            android:insetLeft="0dp"
                            android:insetTop="0dp"
                            android:insetRight="0dp"
                            android:insetBottom="0dp"
                            android:contentDescription="Forward 30 seconds"
                            app:icon="@drawable/ic_forward_30"
                            app:iconSize="32dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnSecondaryContainer"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>
                    </LinearLayout>

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- VOLUME CONTROL SLIDER -->
            <LinearLayout
                android:id="@+id/volumeControlContainer"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                app:layout_constraintTop_toBottomOf="@id/playerControlsCard"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent">

                <ImageView
                    android:id="@+id/ivVolumeIcon"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_volume_up"
                    android:contentDescription="Volume"
                    app:tint="?attr/colorOnSurfaceVariant"/>

                <com.google.android.material.slider.Slider
                    android:id="@+id/volumeSlider"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="12dp"
                    android:layout_marginEnd="12dp"
                    android:valueFrom="0"
                    android:valueTo="100"
                    android:value="50"
                    app:labelBehavior="gone"
                    app:thumbRadius="8dp"
                    app:trackCornerSize="8dp"
                    app:trackHeight="30dp"/>

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvVolumePercent"
                    android:layout_width="40dp"
                    android:layout_height="wrap_content"
                    android:text="50%"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:gravity="end"/>
            </LinearLayout>

            <!-- ACTION BUTTONS -->
            <LinearLayout
                android:id="@+id/actionButtonsContainer"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:orientation="horizontal"
                android:gravity="center"
                app:layout_constraintTop_toBottomOf="@id/volumeControlContainer"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnChangeSong"
                    style="@style/Widget.Material3.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/change_song"
                    app:icon="@drawable/ic_music_note"
                    android:layout_marginEnd="8dp"/>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnSavePreset"
                    style="@style/Widget.Material3.Button.TonalButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/save"
                    app:icon="@drawable/ic_save"/>
            </LinearLayout>

            <!-- BOTTOM SPACER -->
            <Space
                android:layout_width="0dp"
                android:layout_height="32dp"
                app:layout_constraintTop_toBottomOf="@id/actionButtonsContainer"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>

        </androidx.constraintlayout.widget.ConstraintLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\layout\fragment_settings.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/settingsRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    tools:context="com.codetrio.spatialflow.ui.SettingsFragment">

    <androidx.core.widget.NestedScrollView
        android:id="@+id/settingsScroll"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="16dp"
        android:scrollbars="vertical"
        android:fillViewport="true"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <com.google.android.material.textview.MaterialTextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="16dp"
                android:paddingTop="32dp"
                android:paddingEnd="16dp"
                android:text="@string/settings_header_general"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textColor="?attr/colorPrimary" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switchTheme"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="56dp"
                android:checked="true"
                android:paddingStart="16dp"
                android:paddingEnd="16dp"
                android:text="@string/setting_dark_mode"
                android:textAppearance="?attr/textAppearanceBodyLarge"
                app:thumbTint="?attr/colorPrimary"
                app:trackTint="?attr/colorSurfaceVariant" />

            <com.google.android.material.divider.MaterialDivider
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:layout_marginBottom="8dp"
                app:dividerInsetStart="16dp"
                app:dividerInsetEnd="16dp" />

            <com.google.android.material.textview.MaterialTextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="16dp"
                android:paddingTop="16dp"
                android:paddingEnd="16dp"
                android:text="@string/settings_header_about"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textColor="?attr/colorPrimary" />

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/groupUpdateSection"
                style="?attr/materialCardViewFilledStyle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:layout_marginEnd="16dp"
                android:layout_marginTop="12dp"
                app:cardCornerRadius="20dp"
                app:cardElevation="0dp"
                app:cardBackgroundColor="?attr/colorSurfaceContainerHighest">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">

                    <LinearLayout
                        android:id="@+id/rowVersion"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="16dp"
                        android:gravity="center_vertical"
                        android:background="?attr/selectableItemBackground"
                        android:orientation="vertical">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvVersion"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface"
                            tools:text="Version 1.0.0" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnCheckUpdate"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="12dp"
                            android:text="Check for updates"
                            android:textSize="14dp"
                            app:icon="@drawable/ic_update"
                            app:iconSize="30dp"
                            app:cornerRadius="12dp" />

                    </LinearLayout>

                    <!-- Divider -->
                    <View
                        android:layout_width="match_parent"
                        android:layout_height="2dp"
                        android:alpha="1"
                        android:background="?attr/colorSurface" />


                    <LinearLayout
                        android:id="@+id/rowWhatsNew"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="16dp"
                        android:gravity="center_vertical"
                        android:clickable="true"
                        android:focusable="true"
                        android:background="?attr/selectableItemBackground"
                        android:orientation="horizontal">

                        <com.google.android.material.textview.MaterialTextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="What's New"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textColor="?attr/colorOnSurface" />

                        <ImageView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:src="@drawable/ic_chevron_right"
                            android:contentDescription="Expand What's New"
                            app:tint="?attr/colorOnSurfaceVariant" />
                    </LinearLayout>

                </LinearLayout>

            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardCredits"
                style="?attr/materialCardViewOutlinedStyle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="32dp"
                android:layout_marginBottom="24dp"
                android:layout_marginStart="16dp"
                android:layout_marginEnd="16dp"
                app:cardBackgroundColor="?attr/colorSurfaceVariant"
                app:cardCornerRadius="20dp"
                app:strokeWidth="1dp"
                app:strokeColor="?attr/colorOutlineVariant">

                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:padding="24dp">

                    <com.google.android.material.textview.MaterialTextView
                        android:id="@+id/tvCreditsTitle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:text="Developed by"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant"
                        android:gravity="center"
                        app:layout_constraintTop_toTopOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintEnd_toEndOf="parent" />

                    <com.google.android.material.textview.MaterialTextView
                        android:id="@+id/tvDeveloperName"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="Shubham Karande"
                        android:textAppearance="?attr/textAppearanceTitleLarge"
                        android:textColor="?attr/colorOnSurface"
                        android:gravity="center"
                        app:layout_constraintTop_toBottomOf="@id/tvCreditsTitle"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintEnd_toEndOf="parent" />

                    <LinearLayout
                        android:id="@+id/socialLinksContainer"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:orientation="horizontal"
                        android:gravity="center"
                        app:layout_constraintTop_toBottomOf="@id/tvDeveloperName"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintEnd_toEndOf="parent">

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnGitHub"
                            style="@style/Widget.Material3.Button.IconButton.Filled"
                            android:layout_width="56dp"
                            android:layout_height="56dp"
                            android:layout_marginTop="0dp"
                            app:icon="@drawable/ic_github"
                            app:iconSize="28dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnPrimary"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"
                            app:layout_constraintTop_toBottomOf="@id/tvDeveloperName"
                            app:layout_constraintHorizontal_chainStyle="packed"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toStartOf="@+id/btnInstagram" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnInstagram"
                            style="@style/Widget.Material3.Button.IconButton.Filled"
                            android:layout_width="56dp"
                            android:layout_height="56dp"
                            android:layout_marginHorizontal="12dp"
                            app:icon="@drawable/ic_instagram"
                            app:iconSize="25dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnPrimary"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"
                            app:layout_constraintTop_toTopOf="@id/btnGitHub"
                            app:layout_constraintStart_toEndOf="@id/btnGitHub"
                            app:layout_constraintEnd_toStartOf="@+id/btnYoutube" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnYoutube"
                            style="@style/Widget.Material3.Button.IconButton.Filled"
                            android:layout_width="56dp"
                            android:layout_height="56dp"
                            app:icon="@drawable/ic_youtube"
                            app:iconSize="25dp"
                            app:iconGravity="textStart"
                            app:iconPadding="0dp"
                            app:iconTint="?attr/colorOnPrimary"
                            app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"
                            app:layout_constraintTop_toTopOf="@id/btnGitHub"
                            app:layout_constraintStart_toEndOf="@id/btnInstagram"
                            app:layout_constraintEnd_toEndOf="parent" />

                    </LinearLayout>

                </androidx.constraintlayout.widget.ConstraintLayout>

            </com.google.android.material.card.MaterialCardView>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:layout_marginBottom="32dp"
                android:paddingStart="16dp"
                android:paddingEnd="16dp">

                <ImageView
                    android:id="@+id/imgAppLogo"
                    android:layout_width="250dp"
                    android:layout_height="250dp"
                    android:src="@drawable/ic_launcher_foreground"
                    android:contentDescription="@string/app_name"
                    android:scaleType="fitCenter"
                    app:tint="?attr/colorPrimary" />


                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvAppName"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/app_name"
                    android:textSize="40sp"
                    android:textStyle="bold"
                    android:textColor="?attr/colorOnSurface"
                    android:fontFamily="sans-serif-medium"
                    android:letterSpacing="0.02" />

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvCopyright"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="© 2025 Shubham Karande"
                    android:textSize="13sp"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:layout_marginTop="4dp"
                    android:textAppearance="?attr/textAppearanceBodySmall" />

            </LinearLayout>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\layout\item_song.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="72dp"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:gravity="center_vertical"
    android:background="?attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/ivAlbumArt"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:scaleType="centerCrop"
        android:src="@drawable/default_album_art"
        app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.SmallComponent"
        android:contentDescription="@string/album_art" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Song Title"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:textColor="?attr/colorOnSurface"
            android:maxLines="1"
            android:ellipsize="end" />

        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvArtist"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Artist Name"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:maxLines="1"
            android:ellipsize="end" />
    </LinearLayout>

    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/cbSelected"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        android:clickable="false"
        android:focusable="false" />

</LinearLayout>
``

## File: app\src\main\res\layout-land\activity_main.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="false"
    android:background="?attr/colorSurface">

    <!-- NavHost fills the area above the BottomNavigationView -->
    <fragment
        android:id="@+id/nav_host_fragment_activity_main"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/nav_view"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

    <!-- Bottom Navigation Bar -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/nav_view"
        style="@style/Widget.Material3.BottomNavigationView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        app:backgroundTint="?attr/colorSurfaceContainer"
        app:itemIconTint="@color/bottom_nav_item_color"
        app:itemTextColor="@color/bottom_nav_item_color"
        app:itemRippleColor="@color/bottom_nav_ripple_color"
        app:itemActiveIndicatorStyle="@style/BottomNavActiveIndicator"
        app:labelVisibilityMode="labeled"
        app:menu="@menu/bottom_nav_menu"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\layout-land\fragment_effects.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="24dp"
        android:paddingEnd="24dp"
        android:paddingTop="12dp"
        android:paddingBottom="24dp">

        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvHeader"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/audio_effects"
            android:textAppearance="?attr/textAppearanceHeadlineSmall"
            android:textColor="?attr/colorOnSurface" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardProcessing"
            style="?attr/materialCardViewFilledStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorPrimaryContainer"
            app:cardCornerRadius="16dp"
            app:cardElevation="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="12dp"
                android:orientation="vertical">

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvProcessingStatus"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/processing_audio"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnPrimaryContainer"
                    android:fontFamily="sans-serif-medium"
                    android:gravity="center" />

                <com.google.android.material.progressindicator.LinearProgressIndicator
                    android:id="@+id/progressBar"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:indeterminate="false"
                    app:indicatorColor="?attr/colorPrimary"
                    app:trackColor="?attr/colorSurfaceVariant"
                    app:trackThickness="6dp"
                    app:trackCornerRadius="3dp"
                    app:showAnimationBehavior="inward" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:baselineAligned="false"
            android:layout_marginTop="12dp">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:layout_marginEnd="12dp">

                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/cardGroup1"
                    style="?attr/materialCardViewFilledStyle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardBackgroundColor="?attr/colorSurfaceVariant"
                    app:cardCornerRadius="20dp"
                    app:cardElevation="0dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical">

                        <LinearLayout
                            android:id="@+id/section8d"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tv8DTitle"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="@string/eight_d_audio"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.materialswitch.MaterialSwitch
                                    android:id="@+id/switch8D"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    app:thumbTint="?attr/colorPrimary"
                                    app:trackTint="?attr/colorSurfaceVariant"
                                    app:trackDecorationTint="?attr/colorOutlineVariant" />
                            </LinearLayout>

                            <com.google.android.material.textview.MaterialTextView
                                android:id="@+id/tv8DDescription"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="4dp"
                                android:text="@string/eight_d_description"
                                android:textAppearance="?attr/textAppearanceBodySmall"
                                android:textColor="?attr/colorOnSurfaceVariant" />
                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="1dp"
                            android:background="?attr/colorSurface" />

                        <LinearLayout
                            android:id="@+id/sectionBass"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBassTitle"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="@string/bass_boost"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.materialswitch.MaterialSwitch
                                    android:id="@+id/switchBass"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    app:thumbTint="?attr/colorPrimary"
                                    app:trackTint="?attr/colorSurfaceVariant"
                                    app:trackDecorationTint="?attr/colorOutlineVariant" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginTop="8dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBassBoostLabel"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="@string/bass_level"
                                    android:textAppearance="?attr/textAppearanceBodyMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBassBoostValue"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelMedium"
                                    android:textColor="?attr/colorPrimary" />
                            </LinearLayout>

                            <com.google.android.material.slider.Slider
                                android:id="@+id/sliderBassBoost"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="0dp"
                                android:valueFrom="-15"
                                android:valueTo="15"
                                android:value="0"
                                android:stepSize="1"
                                android:enabled="false"
                                app:labelBehavior="gone"
                                app:thumbColor="?attr/colorPrimary"
                                app:trackColorActive="?attr/colorPrimary"
                                app:trackColorInactive="?attr/colorOutlineVariant"
                                app:haloColor="?attr/colorPrimaryContainer"
                                app:trackHeight="12dp"
                                app:thumbRadius="8dp" />
                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="1dp"
                            android:background="?attr/colorSurface" />

                        <LinearLayout
                            android:id="@+id/sectionEqualizer"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvEqualizerTitle"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="5-Band Equalizer"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.materialswitch.MaterialSwitch
                                    android:id="@+id/switchEqualizer"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    app:thumbTint="?attr/colorPrimary"
                                    app:trackTint="?attr/colorSurfaceVariant"
                                    app:trackDecorationTint="?attr/colorOutlineVariant" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginTop="8dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand1Label"
                                    android:layout_width="50dp"
                                    android:layout_height="wrap_content"
                                    android:text="60 Hz"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBand1"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:valueFrom="-12"
                                    android:valueTo="12"
                                    android:value="0"
                                    android:stepSize="1"
                                    android:enabled="false"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:trackColorActive="?attr/colorPrimary"
                                    app:trackColorInactive="?attr/colorOutlineVariant"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="6dp"
                                    app:thumbRadius="6dp" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand1Value"
                                    android:layout_width="40dp"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelSmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:gravity="end" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand2Label"
                                    android:layout_width="50dp"
                                    android:layout_height="wrap_content"
                                    android:text="230 Hz"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBand2"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:valueFrom="-12"
                                    android:valueTo="12"
                                    android:value="0"
                                    android:stepSize="1"
                                    android:enabled="false"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:trackColorActive="?attr/colorPrimary"
                                    app:trackColorInactive="?attr/colorOutlineVariant"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="6dp"
                                    app:thumbRadius="6dp" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand2Value"
                                    android:layout_width="40dp"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelSmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:gravity="end" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand3Label"
                                    android:layout_width="50dp"
                                    android:layout_height="wrap_content"
                                    android:text="910 Hz"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBand3"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:valueFrom="-12"
                                    android:valueTo="12"
                                    android:value="0"
                                    android:stepSize="1"
                                    android:enabled="false"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:trackColorActive="?attr/colorPrimary"
                                    app:trackColorInactive="?attr/colorOutlineVariant"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="6dp"
                                    app:thumbRadius="6dp" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand3Value"
                                    android:layout_width="40dp"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelSmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:gravity="end" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand4Label"
                                    android:layout_width="50dp"
                                    android:layout_height="wrap_content"
                                    android:text="3.6 k"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBand4"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:valueFrom="-12"
                                    android:valueTo="12"
                                    android:value="0"
                                    android:stepSize="1"
                                    android:enabled="false"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:trackColorActive="?attr/colorPrimary"
                                    app:trackColorInactive="?attr/colorOutlineVariant"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="6dp"
                                    app:thumbRadius="6dp" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand4Value"
                                    android:layout_width="40dp"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelSmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:gravity="end" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand5Label"
                                    android:layout_width="50dp"
                                    android:layout_height="wrap_content"
                                    android:text="14 kHz"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBand5"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:valueFrom="-12"
                                    android:valueTo="12"
                                    android:value="0"
                                    android:stepSize="1"
                                    android:enabled="false"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:trackColorActive="?attr/colorPrimary"
                                    app:trackColorInactive="?attr/colorOutlineVariant"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="6dp"
                                    app:thumbRadius="6dp" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBand5Value"
                                    android:layout_width="40dp"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelSmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:gravity="end" />
                            </LinearLayout>

                        </LinearLayout>

                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:layout_marginStart="12dp">

                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/cardGroup2"
                    style="?attr/materialCardViewFilledStyle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardBackgroundColor="?attr/colorSurfaceVariant"
                    app:cardCornerRadius="20dp"
                    app:cardElevation="0dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical">

                        <LinearLayout
                            android:id="@+id/sectionLoudness"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvLoudnessTitle"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Loudness Enhancer"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.materialswitch.MaterialSwitch
                                    android:id="@+id/switchLoudness"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    app:thumbTint="?attr/colorPrimary"
                                    app:trackTint="?attr/colorSurfaceVariant"
                                    app:trackDecorationTint="?attr/colorOutlineVariant" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginTop="8dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvLoudnessLabel"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Gain"
                                    android:textAppearance="?attr/textAppearanceBodyMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvLoudnessValue"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="+0 dB"
                                    android:textAppearance="?attr/textAppearanceLabelMedium"
                                    android:textColor="?attr/colorPrimary" />
                            </LinearLayout>

                            <com.google.android.material.slider.Slider
                                android:id="@+id/sliderLoudness"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="0dp"
                                android:valueFrom="0"
                                android:valueTo="12"
                                android:value="0"
                                android:stepSize="1"
                                android:enabled="false"
                                app:labelBehavior="gone"
                                app:thumbColor="?attr/colorPrimary"
                                app:trackColorActive="?attr/colorPrimary"
                                app:trackColorInactive="?attr/colorOutlineVariant"
                                app:haloColor="?attr/colorPrimaryContainer"
                                app:trackHeight="12dp"
                                app:thumbRadius="8dp" />
                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="1dp"
                            android:background="?attr/colorSurface" />

                        <LinearLayout
                            android:id="@+id/sectionBalance"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBalanceTitle"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Stereo Balance"
                                    android:textAppearance="?attr/textAppearanceTitleMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.materialswitch.MaterialSwitch
                                    android:id="@+id/switchBalance"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    app:thumbTint="?attr/colorPrimary"
                                    app:trackTint="?attr/colorSurfaceVariant"
                                    app:trackDecorationTint="?attr/colorOutlineVariant" />
                            </LinearLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginTop="8dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBalanceLabel"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Position"
                                    android:textAppearance="?attr/textAppearanceBodyMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvBalanceValue"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Center"
                                    android:textAppearance="?attr/textAppearanceLabelMedium"
                                    android:textColor="?attr/colorPrimary" />
                            </LinearLayout>

                            <RelativeLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="4dp">

                                <View
                                    android:id="@+id/balanceCenterLine"
                                    android:layout_width="3dp"
                                    android:layout_height="24dp"
                                    android:layout_centerHorizontal="true"
                                    android:layout_centerVertical="true"
                                    android:background="?attr/colorPrimary" />

                                <com.google.android.material.slider.Slider
                                    android:id="@+id/sliderBalance"
                                    android:layout_width="match_parent"
                                    android:layout_height="wrap_content"
                                    android:valueFrom="-50"
                                    android:valueTo="50"
                                    android:value="0"
                                    android:stepSize="5"
                                    app:labelBehavior="gone"
                                    app:thumbColor="?attr/colorPrimary"
                                    app:haloColor="?attr/colorPrimaryContainer"
                                    app:trackHeight="15dp"
                                    app:thumbRadius="10dp" />
                            </RelativeLayout>

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:paddingStart="16dp"
                                android:paddingEnd="16dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Left"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurfaceVariant"
                                    android:textStyle="bold" />

                                <View
                                    android:layout_width="0dp"
                                    android:layout_height="0dp"
                                    android:layout_weight="1" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Center"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorPrimary"
                                    android:textStyle="bold" />

                                <View
                                    android:layout_width="0dp"
                                    android:layout_height="0dp"
                                    android:layout_weight="1" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Right"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurfaceVariant"
                                    android:textStyle="bold" />
                            </LinearLayout>
                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="1dp"
                            android:background="?attr/colorSurface" />

                        <LinearLayout
                            android:id="@+id/sectionSpeed"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="16dp">

                            <com.google.android.material.textview.MaterialTextView
                                android:id="@+id/tvSpeedTitle"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:paddingTop="2dp"
                                android:text="Playback Speed"
                                android:textAppearance="?attr/textAppearanceTitleMedium"
                                android:textColor="?attr/colorOnSurface" />

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginTop="8dp">

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvSpeedLabel"
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Speed"
                                    android:textAppearance="?attr/textAppearanceBodyMedium"
                                    android:textColor="?attr/colorOnSurface" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:id="@+id/tvSpeedValue"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="1.00x"
                                    android:textAppearance="?attr/textAppearanceLabelMedium"
                                    android:textColor="?attr/colorPrimary" />
                            </LinearLayout>

                            <com.google.android.material.slider.Slider
                                android:id="@+id/sliderSpeed"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="0dp"
                                android:valueFrom="0.5"
                                android:valueTo="2.0"
                                android:value="1.0"
                                android:stepSize="0.05"
                                app:labelBehavior="gone"
                                app:thumbColor="?attr/colorPrimary"
                                app:trackColorActive="?attr/colorPrimary"
                                app:trackColorInactive="?attr/colorOutlineVariant"
                                app:haloColor="?attr/colorPrimaryContainer"
                                app:trackHeight="12dp"
                                app:thumbRadius="8dp" />

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal">

                                <com.google.android.material.textview.MaterialTextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="0.5x"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurfaceVariant" />

                                <View
                                    android:layout_width="0dp"
                                    android:layout_height="0dp"
                                    android:layout_weight="1" />

                                <com.google.android.material.textview.MaterialTextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="2.0x"
                                    android:textAppearance="?attr/textAppearanceBodySmall"
                                    android:textColor="?attr/colorOnSurfaceVariant" />
                            </LinearLayout>
                        </LinearLayout>

                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>

        </LinearLayout>
    </LinearLayout>

</androidx.core.widget.NestedScrollView>
``

## File: app\src\main\res\layout-land\fragment_player.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface">

    <!-- Guideline to split the screen 45/55 -->
    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/guidelineCenter"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintGuide_percent="0.45" />

    <!-- LEFT SIDE: ALBUM ART -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/cardAlbumArt"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_margin="24dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/guidelineCenter"
        app:layout_constraintDimensionRatio="1:1"
        app:cardCornerRadius="28dp"
        app:cardElevation="0dp"
        app:cardBackgroundColor="?attr/colorSurfaceVariant">

        <ImageView
            android:id="@+id/ivAlbumArt"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop"
            android:src="@drawable/ic_launcher_foreground"
            android:contentDescription="Album Art"/>
    </com.google.android.material.card.MaterialCardView>

    <!-- RIGHT SIDE: SCROLLABLE CONTROLS -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:scrollbars="none"
        app:layout_constraintStart_toEndOf="@id/guidelineCenter"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingHorizontal="24dp"
            android:paddingVertical="16dp">

            <!-- SONG TITLE -->
            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvSongName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:text="@string/no_song_selected"
                android:textAppearance="?attr/textAppearanceTitleLarge"
                android:textColor="?attr/colorOnSurface"
                android:textStyle="bold"
                android:gravity="start"
                android:maxLines="1"
                android:ellipsize="marquee"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toStartOf="@+id/chipSongHaptics"
                android:layout_marginEnd="8dp"/>

            <!-- SONG HAPTICS CHIP (Aligned to title end in landscape) -->
            <com.google.android.material.chip.Chip
                android:id="@+id/chipSongHaptics"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:checkable="true"
                android:text="@string/song_haptics"
                app:checkedIcon="@drawable/ic_vibration"
                app:chipIcon="@drawable/ic_vibration_off"
                app:chipIconSize="16dp"
                app:layout_constraintBottom_toBottomOf="@id/tvSongName"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintTop_toTopOf="@id/tvSongName" />

            <!-- SEEK BAR -->
            <com.google.android.material.slider.Slider
                android:id="@+id/seekBar"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:valueFrom="0"
                android:valueTo="100"
                android:value="0"
                app:labelBehavior="gone"
                app:thumbRadius="6dp"
                app:trackHeight="8dp"
                app:layout_constraintTop_toBottomOf="@id/tvSongName"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- TIME LABELS -->
            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvCurrentTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="00:00"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="?attr/colorOnSurfaceVariant"
                app:layout_constraintTop_toBottomOf="@id/seekBar"
                app:layout_constraintStart_toStartOf="parent"/>

            <com.google.android.material.textview.MaterialTextView
                android:id="@+id/tvTotalTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="00:00"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="?attr/colorOnSurfaceVariant"
                app:layout_constraintTop_toBottomOf="@id/seekBar"
                app:layout_constraintEnd_toEndOf="parent"/>

            <!-- PLAYER CONTROLS (Maintaining your Centered Icons code) -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/playerControlsCard"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:layout_constraintTop_toBottomOf="@id/tvCurrentTime"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:cardCornerRadius="36dp"
                app:cardElevation="0dp"
                app:cardBackgroundColor="?attr/colorSurfaceContainerHigh">

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:paddingHorizontal="16dp"
                    android:paddingVertical="12dp"
                    android:gravity="center">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnRewind30"
                        style="@style/Widget.Material3.Button.IconButton.Filled.Tonal"
                        android:layout_width="56dp"
                        android:layout_height="56dp"
                        android:insetLeft="0dp"
                        android:insetTop="0dp"
                        android:insetRight="0dp"
                        android:insetBottom="0dp"
                        app:icon="@drawable/ic_replay_30"
                        app:iconSize="28dp"
                        app:iconGravity="textStart"
                        app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnPlayPauseToggle"
                        style="@style/Widget.Material3.Button.IconButton.Filled"
                        android:layout_width="76dp"
                        android:layout_height="76dp"
                        android:layout_marginHorizontal="16dp"
                        android:insetLeft="0dp"
                        android:insetTop="0dp"
                        android:insetRight="0dp"
                        android:insetBottom="0dp"
                        app:icon="@drawable/ic_pause"
                        app:iconSize="36dp"
                        app:iconGravity="textStart"
                        app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnForward30"
                        style="@style/Widget.Material3.Button.IconButton.Filled.Tonal"
                        android:layout_width="56dp"
                        android:layout_height="56dp"
                        android:insetLeft="0dp"
                        android:insetTop="0dp"
                        android:insetRight="0dp"
                        android:insetBottom="0dp"
                        app:icon="@drawable/ic_forward_30"
                        app:iconSize="28dp"
                        app:iconGravity="textStart"
                        app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- VOLUME CONTROLS (Smaller for Landscape) -->
            <LinearLayout
                android:id="@+id/volumeControlsContainer"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:orientation="horizontal"
                app:layout_constraintTop_toBottomOf="@id/playerControlsCard"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent">

                <com.google.android.material.floatingactionbutton.FloatingActionButton
                    android:id="@+id/btnMute"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:srcCompat="@drawable/ic_volume_off"
                    app:fabSize="mini"
                    app:elevation="0dp"
                    app:backgroundTint="?attr/colorSecondaryContainer"/>

                <com.google.android.material.floatingactionbutton.FloatingActionButton
                    android:id="@+id/btnVolumeDown"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="12dp"
                    app:srcCompat="@drawable/ic_volume_down"
                    app:fabSize="mini"
                    app:elevation="0dp"
                    app:backgroundTint="?attr/colorSecondaryContainer"/>

                <com.google.android.material.floatingactionbutton.FloatingActionButton
                    android:id="@+id/btnVolumeUp"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="12dp"
                    app:srcCompat="@drawable/ic_volume_up"
                    app:fabSize="mini"
                    app:elevation="0dp"
                    app:backgroundTint="?attr/colorSecondaryContainer"/>
            </LinearLayout>

            <!-- ACTION BUTTONS -->
            <LinearLayout
                android:id="@+id/actionButtonsContainer"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:orientation="horizontal"
                app:layout_constraintTop_toBottomOf="@id/volumeControlsContainer"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnChangeSong"
                    style="@style/Widget.Material3.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/change_song"
                    android:layout_marginEnd="8dp"/>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnSavePreset"
                    style="@style/Widget.Material3.Button.TonalButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/save"/>
            </LinearLayout>

        </androidx.constraintlayout.widget.ConstraintLayout>
    </androidx.core.widget.NestedScrollView>

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\layout-land\fragment_settings.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/settingsRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    tools:context="com.codetrio.spatialflow.ui.SettingsFragment">

    <androidx.core.widget.NestedScrollView
        android:id="@+id/settingsScroll"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="12dp"
        android:scrollbars="vertical"
        android:fillViewport="true"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:paddingTop="16dp">

            <!-- LEFT COLUMN: settings controls -->
            <LinearLayout
                android:id="@+id/leftContainer"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:layout_marginEnd="16dp"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toStartOf="@id/rightContainer"
                app:layout_constraintWidth_percent="0.54">

                <com.google.android.material.textview.MaterialTextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_header_general"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorPrimary"
                    android:paddingBottom="8dp"/>

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/switchTheme"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:minHeight="52dp"
                    android:paddingStart="8dp"
                    android:paddingEnd="8dp"
                    android:text="@string/setting_dark_mode"
                    android:checked="true"
                    android:textAppearance="?attr/textAppearanceBodyLarge"
                    app:thumbTint="?attr/colorPrimary"
                    app:trackTint="?attr/colorSurfaceVariant"/>

                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:layout_marginBottom="8dp"/>

                <com.google.android.material.textview.MaterialTextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingTop="8dp"
                    android:text="@string/settings_header_about"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorPrimary"
                    android:paddingBottom="8dp"/>

                <!-- Grouped Update Section (keeps same ids) -->
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/groupUpdateSection"
                    style="?attr/materialCardViewFilledStyle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:cardBackgroundColor="?attr/colorSurfaceContainerHighest"
                    app:cardCornerRadius="14dp"
                    app:cardElevation="0dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical">

                        <!-- Version row (vertical content) -->
                        <LinearLayout
                            android:id="@+id/rowVersion"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:padding="14dp"
                            android:orientation="vertical"
                            android:background="?attr/selectableItemBackground">

                            <com.google.android.material.textview.MaterialTextView
                                android:id="@+id/tvVersion"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:textAppearance="?attr/textAppearanceBodyMedium"
                                android:textColor="?attr/colorOnSurface"
                                tools:text="Version 1.0.0"/>

                            <com.google.android.material.button.MaterialButton
                                android:id="@+id/btnCheckUpdate"
                                style="@style/Widget.Material3.Button.TonalButton"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="10dp"
                                android:text="@string/whats_new_positive_button"
                                android:textAllCaps="false"
                                android:textSize="13sp"
                                app:iconSize="18dp"
                                app:icon="@drawable/ic_update"
                                app:cornerRadius="10dp"/>

                        </LinearLayout>

                        <View
                            android:layout_width="match_parent"
                            android:layout_height="2dp"
                            android:alpha="1"
                            android:background="?attr/colorSurface" />

                        <!-- What's New row (clickable) -->
                        <LinearLayout
                            android:id="@+id/cardWhatsNew"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:padding="14dp"
                            android:orientation="horizontal"
                            android:gravity="center_vertical"
                            android:clickable="true"
                            android:focusable="true"
                            android:background="?attr/selectableItemBackground">

                            <com.google.android.material.textview.MaterialTextView
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:text="@string/whats_new_title"
                                android:textAppearance="?attr/textAppearanceBodyLarge"
                                android:textColor="?attr/colorOnSurface"
                                tools:text="What's New" />

                            <ImageView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:src="@drawable/ic_chevron_right"
                                android:contentDescription="@string/whats_new_positive_button"
                                app:tint="?attr/colorOnSurfaceVariant"/>
                        </LinearLayout>

                    </LinearLayout>

                </com.google.android.material.card.MaterialCardView>

                <!-- Credits/ Social -->
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/cardCredits"
                    style="?attr/materialCardViewOutlinedStyle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    app:cardBackgroundColor="?attr/colorSurfaceVariant"
                    app:cardCornerRadius="16dp"
                    app:strokeWidth="1dp"
                    app:strokeColor="?attr/colorOutlineVariant">

                    <androidx.constraintlayout.widget.ConstraintLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="18dp">

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvCreditsTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:text="Developed by"
                            android:textAppearance="?attr/textAppearanceBodySmall"
                            android:textColor="?attr/colorOnSurfaceVariant"
                            android:gravity="center"
                            app:layout_constraintTop_toTopOf="parent"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toEndOf="parent"/>

                        <com.google.android.material.textview.MaterialTextView
                            android:id="@+id/tvDeveloperName"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="6dp"
                            android:text="Shubham Karande"
                            android:textAppearance="?attr/textAppearanceTitleMedium"
                            android:textColor="?attr/colorOnSurface"
                            android:gravity="center"
                            app:layout_constraintTop_toBottomOf="@id/tvCreditsTitle"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toEndOf="parent"/>

                        <LinearLayout
                            android:id="@+id/socialLinksContainer"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="16dp"
                            android:orientation="horizontal"
                            android:gravity="center"
                            app:layout_constraintTop_toBottomOf="@id/tvDeveloperName"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toEndOf="parent">

                            <com.google.android.material.button.MaterialButton
                                android:id="@+id/btnGitHub"
                                style="@style/Widget.Material3.Button.IconButton.Filled"
                                android:layout_width="48dp"
                                android:layout_height="48dp"
                                android:layout_marginEnd="10dp"
                                app:icon="@drawable/ic_github"
                                app:iconSize="20dp"
                                app:iconTint="?attr/colorOnPrimary"
                                app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"/>

                            <com.google.android.material.button.MaterialButton
                                android:id="@+id/btnInstagram"
                                style="@style/Widget.Material3.Button.IconButton.Filled"
                                android:layout_width="48dp"
                                android:layout_height="48dp"
                                android:layout_marginEnd="10dp"
                                app:icon="@drawable/ic_instagram"
                                app:iconSize="20dp"
                                app:iconTint="?attr/colorOnPrimary"
                                app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"/>

                            <com.google.android.material.button.MaterialButton
                                android:id="@+id/btnYoutube"
                                style="@style/Widget.Material3.Button.IconButton.Filled"
                                android:layout_width="48dp"
                                android:layout_height="48dp"
                                app:icon="@drawable/ic_youtube"
                                app:iconSize="20dp"
                                app:iconTint="?attr/colorOnPrimary"
                                app:shapeAppearanceOverlay="@style/ShapeAppearance.SpatialFlow.CircleButton"/>

                        </LinearLayout>

                    </androidx.constraintlayout.widget.ConstraintLayout>

                </com.google.android.material.card.MaterialCardView>

            </LinearLayout>

            <!-- RIGHT COLUMN: app branding -->
            <LinearLayout
                android:id="@+id/rightContainer"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:layout_marginStart="16dp"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintStart_toEndOf="@id/leftContainer"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintWidth_percent="0.46"
                app:layout_constraintVertical_bias="0.4">

                <ImageView
                    android:id="@+id/imgAppLogo"
                    android:layout_width="180dp"
                    android:layout_height="180dp"
                    android:src="@drawable/ic_launcher_foreground"
                    android:contentDescription="@string/app_name"
                    android:scaleType="fitCenter"
                    app:tint="?attr/colorPrimary"/>

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvAppName"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/app_name"
                    android:textSize="36sp"
                    android:textStyle="bold"
                    android:textColor="?attr/colorOnSurface"
                    android:layout_marginTop="12dp"
                    android:fontFamily="sans-serif-medium"
                    android:letterSpacing="0.02"/>

                <com.google.android.material.textview.MaterialTextView
                    android:id="@+id/tvCopyright"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="© 2025 Shubham Karande"
                    android:textSize="12sp"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:layout_marginTop="4dp"
                    android:textAppearance="?attr/textAppearanceBodySmall"/>

            </LinearLayout>

        </androidx.constraintlayout.widget.ConstraintLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.constraintlayout.widget.ConstraintLayout>
``

## File: app\src\main\res\menu\bottom_nav_menu.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@+id/navigation_player"
        android:icon="@drawable/ic_music_note"
        android:title="@string/tab_player" />

    <item
        android:id="@+id/navigation_effects"
        android:icon="@drawable/ic_equalizer"
        android:title="@string/tab_effects" />

    <item
        android:id="@+id/navigation_settings"
        android:icon="@drawable/ic_settings"
        android:title="@string/tab_settings" />

</menu>
``

## File: app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
``

## File: app\src\main\res\mipmap-anydpi-v26\ic_launcher_round.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
``

## File: app\src\main\res\navigation\nav_graph.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/navigation_player">

    <fragment
        android:id="@+id/navigation_player"
        android:name="com.codetrio.spatialflow.ui.PlayerFragment"
        android:label="Player" />

    <fragment
        android:id="@+id/navigation_effects"
        android:name="com.codetrio.spatialflow.ui.EffectsFragment"
        android:label="Effects" />

    <fragment
        android:id="@+id/navigation_settings"
        android:name="com.codetrio.spatialflow.ui.SettingsFragment"
        android:label="Settings" />

</navigation>
``

## File: app\src\main\res\values\arrays.xml

``xml
<resources>
    <!-- Reply Preference -->
    <string-array name="reply_entries">
        <item>Reply</item>
        <item>Reply to all</item>
    </string-array>

    <string-array name="reply_values">
        <item>reply</item>
        <item>reply_all</item>
    </string-array>



</resources>
``

## File: app\src\main\res\values\colors.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- 🌈 Base seed (only used as fallback on pre-Android 12) -->
    <!-- 🌞 LIGHT MODE FALLBACK -->
    <color name="md_theme_primary">#6750A4</color>
    <color name="md_theme_light_primary">#006874</color>
    <color name="md_theme_light_onPrimary">#FFFFFF</color>
    <color name="md_theme_light_primaryContainer">#97F0FF</color>
    <color name="md_theme_light_onPrimaryContainer">#001F24</color>

    <color name="md_theme_light_secondary">#4A6267</color>
    <color name="md_theme_light_onSecondary">#FFFFFF</color>
    <color name="md_theme_light_secondaryContainer">#CCE8EC</color>
    <color name="md_theme_light_onSecondaryContainer">#051F23</color>

    <color name="md_theme_light_tertiary">#525E7D</color>
    <color name="md_theme_light_onTertiary">#FFFFFF</color>
    <color name="md_theme_light_tertiaryContainer">#DAE2FF</color>
    <color name="md_theme_light_onTertiaryContainer">#0E1B37</color>

    <color name="md_theme_light_background">#FAFDFD</color>
    <color name="md_theme_light_surface">#FAFDFD</color>
    <color name="md_theme_light_surfaceVariant">#DBE4E6</color>
    <color name="md_theme_light_onSurface">#191C1D</color>
    <color name="md_theme_light_onSurfaceVariant">#4A6267</color>


    <!-- 🌑 DARK MODE FALLBACK -->
    <color name="md_theme_dark_primary">#4FD8EB</color>
    <color name="md_theme_dark_onPrimary">#00363D</color>
    <color name="md_theme_dark_primaryContainer">#004F58</color>
    <color name="md_theme_dark_onPrimaryContainer">#97F0FF</color>

    <color name="md_theme_dark_secondary">#B1CCCF</color>
    <color name="md_theme_dark_onSecondary">#1C3438</color>
    <color name="md_theme_dark_secondaryContainer">#334B4F</color>
    <color name="md_theme_dark_onSecondaryContainer">#CCE8EC</color>

    <color name="md_theme_dark_tertiary">#BAC6EA</color>
    <color name="md_theme_dark_onTertiary">#24304D</color>
    <color name="md_theme_dark_tertiaryContainer">#3B4664</color>
    <color name="md_theme_dark_onTertiaryContainer">#DAE2FF</color>

    <color name="md_theme_dark_background">#191C1D</color>
    <color name="md_theme_dark_surface">#191C1D</color>
    <color name="md_theme_dark_surfaceVariant">#3F484A</color>
    <color name="md_theme_dark_onSurface">#E1E3E3</color>
    <color name="md_theme_dark_onSurfaceVariant">#CCE8EC</color>
    <color name="splash_background">#FFFFFF</color>
    <color name="splash_icon_tint">#000000</color>

</resources>
``

## File: app\src\main\res\values\dimens.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Responsive percentages (ConstraintLayout) -->
    <!-- Reduced album art size to prevent excessive scrolling -->
    <dimen name="album_art_width_percent">0.85</dimen>
    <dimen name="album_art_height_percent">0.48</dimen>

    <!-- Controls area - optimized for scrollable content -->
    <dimen name="controls_max_width_percent">0.90</dimen>

    <!-- Fixed Material Design base values -->
    <!-- Reduced album art dimensions for better scroll performance -->
    <dimen name="album_art_min_size">200dp</dimen>
    <dimen name="album_art_max_size">340dp</dimen>
    <dimen name="album_art_corner_radius">24dp</dimen>
    <dimen name="album_art_elevation">4dp</dimen>

    <!-- Horizontal screen margin - reduced for more content space -->
    <dimen name="margin_screen_horizontal">16dp</dimen>
    <dimen name="margin_bottom_spacing">16dp</dimen>

    <!-- Generic margins - optimized for scrollable layout -->
    <dimen name="margin_xsmall">2dp</dimen>
    <dimen name="margin_small">6dp</dimen>
    <dimen name="margin_medium">12dp</dimen>
    <dimen name="margin_large">16dp</dimen>
    <dimen name="margin_xlarge">24dp</dimen>

    <!-- Vertical relationships - REDUCED for better scroll fit -->
    <dimen name="margin_between_album_title">12dp</dimen>
    <dimen name="margin_between_title_slider">8dp</dimen>
    <dimen name="margin_between_slider_controls">20dp</dimen>
    <dimen name="margin_between_controls_actions">16dp</dimen>
    <dimen name="margin_between_control_buttons">12dp</dimen>
    <dimen name="margin_between_action_buttons">8dp</dimen>
    <dimen name="margin_time_labels">4dp</dimen>

    <!-- Top and bottom padding - REDUCED to minimize scroll distance -->
    <dimen name="padding_top_content">16dp</dimen>
    <dimen name="padding_bottom_content">16dp</dimen>

    <!-- Text sizes (use autoSizeTextType="uniform" on title) -->
    <dimen name="song_title_min_text_size">16sp</dimen>
    <dimen name="song_title_max_text_size">22sp</dimen>
    <dimen name="time_label_text_size">11sp</dimen>

    <!-- Slider look - slightly thinner for compact layout -->
    <dimen name="slider_track_height">8dp</dimen>
    <dimen name="slider_thumb_radius">6dp</dimen>
    <dimen name="chip_icon_size">16dp</dimen>
    <dimen name="chip_text_size">13sp</dimen>

    <!-- Buttons / FABs - REDUCED for compact scrollable layout -->
    <dimen name="button_height">44dp</dimen>
    <dimen name="button_text_size">13sp</dimen>
    <dimen name="button_icon_size">18dp</dimen>

    <!-- Main play button - slightly smaller to reduce scroll -->
    <dimen name="fab_play_size">64dp</dimen>
    <dimen name="fab_play_icon_size">30dp</dimen>

    <dimen name="fab_stop_size">52dp</dimen>
    <dimen name="fab_stop_icon_size">22dp</dimen>

    <!-- Volume FAB sizes (mini FABs) -->
    <dimen name="fab_volume_size">40dp</dimen>
    <dimen name="fab_volume_icon_size">20dp</dimen>

    <!-- Max widths (for large tablets) -->
    <dimen name="controls_max_width">580dp</dimen>

</resources>
``

## File: app\src\main\res\values\strings.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- App name -->
    <string name="app_name">SpatialFlow</string>

    <!-- Bottom tabs -->
    <string name="tab_player">Player</string>
    <string name="tab_effects">Effects</string>
    <string name="tab_settings">Settings</string>

    <!-- Player fragment -->
    <string name="album_art">Album Art</string>
    <string name="no_song_selected">No Song Selected</string>
    <string name="select_song">Select Song</string>
    <string name="change_song">Load Song</string>
    <string name="save">Save</string>
    <string name="cd_play_pause">Play or Pause Music</string>
    <string name="cd_stop">Stop Music Playback</string>

    <!-- Effects fragment -->
    <string name="audio_effects">Audio Effects</string>
    <string name="eight_d_audio">8D Audio</string>
    <string name="eight_d_description">Creates circular panning effect - sound rotates around your head in 3D space</string>
    <string name="rotation_speed">Rotation Speed</string>
    <string name="bass_boost">Bass Boost</string>
    <string name="bass_description">Enhance or reduce low frequency sounds. Adds punch and depth to your music</string>
    <string name="bass_level">Bass Level</string>
    <string name="processing_audio">Processing audio with effects...</string>
    <string name="select_bass_level">Select level</string>

    <!-- Settings headers -->
    <string name="settings_header_general">General</string>
    <string name="settings_header_about">About</string>

    <!-- General settings -->
    <string name="setting_dark_mode">Dark Mode</string>
    <string name="setting_audio_focus">Handle Audio Focus</string>

    <!-- About settings -->
    <string name="setting_version_placeholder">App Version</string>
    <string name="setting_licenses">Open Source Licenses</string>

    <!-- Example message/sync prefs (if still used) -->
    <string name="messages_header">Messages</string>
    <string name="sync_header">Sync</string>
    <string name="signature_title">Your signature</string>
    <string name="reply_title">Default reply action</string>
    <string name="sync_title">Sync email periodically</string>
    <string name="attachment_title">Download incoming attachments</string>
    <string name="attachment_summary_on">Automatically download attachments for incoming emails</string>
    <string name="attachment_summary_off">Only download attachments when manually requested</string>

    <!-- What's New dialog -->
    <string name="whats_new_title">What\'s New in Version %1$s</string>
    <string name="whats_new_positive_button">Got It</string>
    <string name="whats_new_content_template"><![CDATA[
        <b>Version %1$s Highlights:</b><ul>
        <li> Added Song Haptics (Exclusive Feature)</li>
        <li> Changed Player Controls (Added 30sec Forward and Backward Skip)</li>
        <li> Added Volume Slider For Better UX</li>
        <li> Improved 8D Processing</li>
        <li> Seekbar Changed to Progressbar(Wavy)</li>
        <li> Improved Audio Effects</li>
        <li> Added Update Checker On App Start</li>
        </ul>]]></string>
    <string name="hello_blank_fragment">Hello blank fragment</string>
    <string name="song_haptics">Song Haptics</string>



</resources>
``

## File: app\src\main\res\values\styles.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">

    <!-- Portrait Toolbar Title -->
    <style name="ToolbarTitleStyle" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">20sp</item>
        <item name="android:textStyle">bold</item>
        <item name="fontFamily">sans-serif-medium</item>

    </style>
    <style name="Base.Theme.SpatialFlow" parent="Theme.Material3.DayNight">
        <item name="colorPrimary">@color/md_theme_primary</item>

        <!-- System bars styling -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowLightNavigationBar" tools:ignore="NewApi">true</item>
        <item name="android:enforceNavigationBarContrast" tools:ignore="NewApi">false</item>
    <!-- Add this line -->
    <item name="linearProgressIndicatorStyle">@style/Widget.Material3Expressive.LinearProgressIndicator.Wavy</item>
    </style>
    <!-- Landscape Toolbar Title - Smaller -->
    <style name="ToolbarTitleStyleLandscape" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textSize">16sp</item>
        <item name="android:textStyle">normal</item>
        <item name="fontFamily">sans-serif-medium</item>
    </style>
    <style name="ShapeAppearance.SpatialFlow.CircleButton" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">50%</item>
    </style>


        <style name="BottomNavActiveIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
            <item name="android:color">?attr/colorSecondaryContainer</item>
            <item name="shapeAppearance">@style/ShapeAppearance.Material3.Corner.Full</item>
            <item name="android:width">64dp</item>
            <item name="android:height">32dp</item>
        </style>

    <style name="AppModalStyle" parent="Widget.Material3.BottomSheet.Modal">
        <item name="backgroundTint">?attr/colorSurfaceContainerLow</item>
    </style>

    <style name="ShapeAppearance.SpatialFlow.BottomSheet" parent="ShapeAppearance.Material3.MediumComponent">
        <item name="cornerSizeTopLeft">32dp</item>
        <item name="cornerSizeTopRight">32dp</item>
        <item name="cornerSizeBottomLeft">0dp</item>
        <item name="cornerSizeBottomRight">0dp</item>
    </style>
</resources>
``

## File: app\src\main\res\values\themes.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Light app theme -->
    <style name="Theme.SpatialFlow" parent="Theme.Material3.DynamicColors.Light.NoActionBar">
        <!-- Status Bar -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>

        <!-- Navigation Bar -->
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightNavigationBar">true</item>

        <!-- Edge-to-edge -->
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>

        <!-- Material 3 color scheme -->
        <item name="colorPrimary">@color/md_theme_light_primary</item>
        <item name="colorOnPrimary">@color/md_theme_light_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_light_primaryContainer</item>
        <item name="colorOnPrimaryContainer">@color/md_theme_light_onPrimaryContainer</item>

        <item name="colorSecondary">@color/md_theme_light_secondary</item>
        <item name="colorOnSecondary">@color/md_theme_light_onSecondary</item>
        <item name="colorSecondaryContainer">@color/md_theme_light_secondaryContainer</item>
        <item name="colorOnSecondaryContainer">@color/md_theme_light_onSecondaryContainer</item>

        <item name="colorTertiary">@color/md_theme_light_tertiary</item>
        <item name="colorOnTertiary">@color/md_theme_light_onTertiary</item>
        <item name="colorTertiaryContainer">@color/md_theme_light_tertiaryContainer</item>
        <item name="colorOnTertiaryContainer">@color/md_theme_light_onTertiaryContainer</item>

        <item name="android:colorBackground">@color/md_theme_light_background</item>
        <item name="colorSurface">@color/md_theme_light_surface</item>
        <item name="colorSurfaceVariant">@color/md_theme_light_surfaceVariant</item>
        <item name="colorOnSurface">@color/md_theme_light_onSurface</item>
        <item name="colorOnSurfaceVariant">@color/md_theme_light_onSurfaceVariant</item>

    </style>
</resources>
``

## File: app\src\main\res\values-land\dimens.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Optimized spacing for landscape - reduced for better fit -->
    <dimen name="margin_screen_horizontal">20dp</dimen>
    <dimen name="margin_large">16dp</dimen>
    <dimen name="margin_medium">12dp</dimen>
    <dimen name="margin_small">6dp</dimen>
    <dimen name="margin_xsmall">4dp</dimen>

    <!-- Album art proportions for landscape - slightly smaller -->
    <dimen name="album_art_width_percent">0.32</dimen>
    <dimen name="album_art_height_percent">0.75</dimen>
    <dimen name="album_art_corner_radius">18dp</dimen>
    <dimen name="album_art_max_size">280dp</dimen>
    <dimen name="album_art_min_size">180dp</dimen>
    <dimen name="album_art_elevation">3dp</dimen>

    <!-- Vertical spacing - REDUCED for landscape fit -->
    <dimen name="margin_between_album_title">8dp</dimen>
    <dimen name="margin_between_title_slider">6dp</dimen>
    <dimen name="margin_between_slider_controls">12dp</dimen>
    <dimen name="margin_between_controls_actions">12dp</dimen>
    <dimen name="margin_between_control_buttons">8dp</dimen>
    <dimen name="margin_between_action_buttons">6dp</dimen>
    <dimen name="margin_time_labels">2dp</dimen>

    <!-- Top and bottom padding - minimal for landscape -->
    <dimen name="padding_top_content">12dp</dimen>
    <dimen name="padding_bottom_content">12dp</dimen>
    <dimen name="margin_bottom_spacing">12dp</dimen>

    <!-- Text sizes - compact for landscape -->
    <dimen name="song_title_min_text_size">14sp</dimen>
    <dimen name="song_title_max_text_size">18sp</dimen>
    <dimen name="time_label_text_size">10sp</dimen>
    <dimen name="chip_text_size">12sp</dimen>

    <!-- Slider - thinner for landscape compactness -->
    <dimen name="slider_track_height">6dp</dimen>
    <dimen name="slider_thumb_radius">5dp</dimen>
    <dimen name="chip_icon_size">14dp</dimen>

    <!-- Main Controls - compact but usable -->
    <dimen name="fab_play_size">56dp</dimen>
    <dimen name="fab_play_icon_size">26dp</dimen>
    <dimen name="fab_stop_size">48dp</dimen>
    <dimen name="fab_stop_icon_size">20dp</dimen>

    <!-- Volume FABs - mini size for landscape -->
    <dimen name="fab_volume_size">36dp</dimen>
    <dimen name="fab_volume_icon_size">18dp</dimen>

    <!-- Action buttons - reduced height -->
    <dimen name="button_height">40dp</dimen>
    <dimen name="button_text_size">12sp</dimen>
    <dimen name="button_icon_size">16dp</dimen>

    <!-- Controls max width for landscape -->
    <dimen name="controls_max_width">500dp</dimen>
    <dimen name="controls_max_width_percent">0.88</dimen>

</resources>
``

## File: app\src\main\res\values-night\themes.xml

``xml
<resources>
    <style name="Theme.SpatialFlow" parent="Theme.Material3.DynamicColors.Dark.NoActionBar">
        <!-- Status Bar -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>

        <!-- Navigation Bar -->
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightNavigationBar">false</item>

        <!-- Enable edge-to-edge -->
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>

        <!-- Material 3 Dynamic Colors -->
        <item name="colorPrimary">@color/md_theme_dark_primary</item>
        <item name="colorOnPrimary">@color/md_theme_dark_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_dark_primaryContainer</item>
        <item name="colorOnPrimaryContainer">@color/md_theme_dark_onPrimaryContainer</item>

        <item name="colorSecondary">@color/md_theme_dark_secondary</item>
        <item name="colorOnSecondary">@color/md_theme_dark_onSecondary</item>
        <item name="colorSecondaryContainer">@color/md_theme_dark_secondaryContainer</item>
        <item name="colorOnSecondaryContainer">@color/md_theme_dark_onSecondaryContainer</item>

        <item name="colorTertiary">@color/md_theme_dark_tertiary</item>
        <item name="colorOnTertiary">@color/md_theme_dark_onTertiary</item>
        <item name="colorTertiaryContainer">@color/md_theme_dark_tertiaryContainer</item>
        <item name="colorOnTertiaryContainer">@color/md_theme_dark_onTertiaryContainer</item>

        <item name="android:colorBackground">@color/md_theme_dark_background</item>
        <item name="colorSurface">@color/md_theme_dark_surface</item>
        <item name="colorSurfaceVariant">@color/md_theme_dark_surfaceVariant</item>
        <item name="colorOnSurface">@color/md_theme_dark_onSurface</item>
        <item name="colorOnSurfaceVariant">@color/md_theme_dark_onSurfaceVariant</item>
    </style>
</resources>
``

## File: app\src\main\res\xml\backup_rules.xml

``xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older than API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
``

## File: app\src\main\res\xml\data_extraction_rules.xml

``xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample data extraction rules file; uncomment and customize as necessary.
   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
   for details.
-->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up.
        <include .../>
        <exclude .../>
        -->
    </cloud-backup>
    <!--
    <device-transfer>
        <include .../>
        <exclude .../>
    </device-transfer>
    -->
</data-extraction-rules>
``

## File: app\src\main\res\xml\file_paths.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="downloads" path="Download/" />
</paths>
``

## File: app\src\main\res\xml\root_preferences.xml

``xml
<PreferenceScreen xmlns:app="http://schemas.android.com/apk/res-auto">

    <PreferenceCategory app:title="@string/messages_header">

        <EditTextPreference
            app:key="signature"
            app:title="@string/signature_title"
            app:useSimpleSummaryProvider="true" />

        <ListPreference
            app:defaultValue="reply"
            app:entries="@array/reply_entries"
            app:entryValues="@array/reply_values"
            app:key="reply"
            app:title="@string/reply_title"
            app:useSimpleSummaryProvider="true" />

    </PreferenceCategory>

    <PreferenceCategory app:title="@string/sync_header">

        <SwitchPreferenceCompat
            app:key="sync"
            app:title="@string/sync_title" />

        <SwitchPreferenceCompat
            app:dependency="sync"
            app:key="attachment"
            app:summaryOff="@string/attachment_summary_off"
            app:summaryOn="@string/attachment_summary_on"
            app:title="@string/attachment_title" />

    </PreferenceCategory>
    <PreferenceCategory app:title="@string/messages_header">

        <EditTextPreference
            app:key="signature"
            app:title="@string/signature_title"
            app:useSimpleSummaryProvider="true" />

        <ListPreference
            app:defaultValue="reply"
            app:entries="@array/reply_entries"
            app:entryValues="@array/reply_values"
            app:key="reply"
            app:title="@string/reply_title"
            app:useSimpleSummaryProvider="true" />

    </PreferenceCategory>
    <PreferenceCategory app:title="@string/sync_header">

        <SwitchPreferenceCompat
            app:key="sync"
            app:title="@string/sync_title" />

        <SwitchPreferenceCompat
            app:dependency="sync"
            app:key="attachment"
            app:summaryOff="@string/attachment_summary_off"
            app:summaryOn="@string/attachment_summary_on"
            app:title="@string/attachment_title" />

    </PreferenceCategory>

</PreferenceScreen>
``

## File: app\src\main\AndroidManifest.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>

    <application
        android:name=".SpatialFlowApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SpatialFlow"
        android:enableOnBackInvokedCallback="true"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:resizeableActivity="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:label="@string/app_name"
            android:theme="@style/Theme.SpatialFlow">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

        <service
            android:name=".service.AudioPlaybackService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <receiver
            android:name="androidx.media.session.MediaButtonReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_BUTTON" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".update.UpdateReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.DOWNLOAD_COMPLETE" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
``

## File: app\build.gradle

``groovy
plugins {
    id 'com.android.application'
}

// Read env vars once (works on CI and locally)
def ciStoreFile = System.getenv("ANDROID_KEYSTORE_PATH")
def ciStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
def ciKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
def ciKeyPassword = System.getenv("ANDROID_KEY_ALIAS_PASSWORD")

android {
    namespace 'com.codetrio.spatialflow'
    compileSdk 36

    defaultConfig {
        applicationId "com.codetrio.spatialflow"
        minSdk 24
        targetSdk 35
        versionCode 6
        versionName "1.5"
    }

    signingConfigs {
        release {
            if (ciStoreFile != null) {
                // CI (GitHub Actions)
                storeFile file(ciStoreFile)
                storePassword ciStorePassword
                keyAlias ciKeyAlias
                keyPassword ciKeyPassword
            } else {
                // Local build on your PC
                storeFile file("C:/Users/shubh/Documents/SpatialFlow.jks")
                storePassword "Shubham@k124"
                keyAlias "ShubhamKarande"
                keyPassword "Shubham@k124"
            }
        }
    }

    buildFeatures {
        viewBinding true
        buildConfig true
    }

    buildTypes {
        release {
            minifyEnabled false
            shrinkResources false
            signingConfig signingConfigs.release
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }

        debug {
            signingConfig signingConfigs.release
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
configurations {
    all {
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])

    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.24'
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    implementation 'androidx.palette:palette:1.0.0'


    implementation 'com.google.android.material:material:1.14.0-alpha08'
    implementation 'androidx.core:core-splashscreen:1.0.1'
    implementation 'androidx.core:core-ktx:1.15.0'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.0'

    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.7'

    implementation 'androidx.viewpager2:viewpager2:1.1.0'
    implementation 'androidx.media:media:1.7.0'
    implementation "androidx.navigation:navigation-fragment:2.7.0"
    implementation "androidx.navigation:navigation-ui:2.7.0"

    implementation libs.preference

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
}
``

## File: gradle\wrapper\gradle-wrapper.properties

``properties
#Sun Oct 19 18:10:54 IST 2025
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
``

## File: gradle\libs.versions.toml

``toml
[versions]
agp = "8.13.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
appcompat = "1.7.1"
material = "1.13.0"
activity = "1.11.0"
constraintlayout = "2.2.1"
preference = "1.2.0"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
activity = { group = "androidx.activity", name = "activity", version.ref = "activity" }
constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
preference = { group = "androidx.preference", name = "preference", version.ref = "preference" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }

``

## File: build.gradle

``groovy
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}
ext {
    mobileFfmpegFullVersion = '4.4.LTS'
}
``

## File: gradle.properties

``properties
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Gradle settings configured through the IDE *will override*
# any settings specified in this file.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. For more details, visit
# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
# org.gradle.parallel=true
# AndroidX package structure to make it clearer which packages are bundled with the
# Android operating system, and which are packaged with your app's APK
# https://developer.android.com/topic/libraries/support-library/androidx-rn
android.useAndroidX=true
# Enables namespacing of each library's R class so that its R class includes only the
# resources declared in the library itself and none from the library's dependencies,
# thereby reducing the size of the R class for that library
android.nonTransitiveRClass=true
``

## File: README.md

``md
<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120"/>
</p>

<h1 align="center">SpatialFlow</h1>

<p align="center">
  A modern, lightweight Android music player built with Material Design 3, smooth animations, and a clean immersive UI.
</p>

<p align="center">
  <b>Fast • Minimal • Material You • Offline Audio Player • Open Source</b>
</p>

<p align="center">
  <!-- Downloads & Release -->
  <img src="https://img.shields.io/github/downloads/MythicalSHUB/SpatialFlow/total?color=5C7AEA&style=for-the-badge" />
  <img src="https://img.shields.io/github/v/release/MythicalSHUB/SpatialFlow?color=4ADE80&style=for-the-badge" />
  <img src="https://img.shields.io/github/actions/workflow/status/MythicalSHUB/SpatialFlow/release.yml?style=for-the-badge&label=BUILD" />

  <!-- Repo Health -->

  <img src="https://img.shields.io/github/issues/MythicalSHUB/SpatialFlow?color=EF4444&style=for-the-badge" />

  <!-- Tech Stack -->

  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Material_You-757575?style=for-the-badge&logo=materialdesign&logoColor=white" />

  <!-- License -->

  <img src="https://img.shields.io/github/license/MythicalSHUB/SpatialFlow?color=10B981&style=for-the-badge" />
</p>

---

## About SpatialFlow

**SpatialFlow** is a modern Android music player designed for a clean, distraction-free listening experience.
Built with Material Design 3 (Material You), it delivers smooth animations, dynamic colors, and high-performance local audio playback.

Perfect for users who want:

* Fast and lightweight music playback
* Clean UI with dynamic theming
* Offline audio player without clutter

---

## Features

* Offline music playback (MP3 + all Android-supported formats)
* Material You dynamic colors
* Lightweight and fast performance
* Built-in app updater (GitHub Releases)
* Glassmorphism-inspired UI
* Smooth animations and transitions
* Local storage music library
* Optimized for battery efficiency

---

## Tech Stack

| Component    | Details                                                      |
| ------------ | ------------------------------------------------------------ |
| Platform     | Android                                                      |
| Language     | Java + Kotlin Stdlib                                         |
| UI Framework | Material Design 3                                            |
| Architecture | Single-module                                                |
| Audio Engine | Android Media APIs                                           |
| Libraries    | AndroidX, Material Components, ViewBinding, FFmpeg Audio Kit |

---

## Requirements

| Component  | Version           |
| ---------- | ----------------- |
| Min SDK    | 24 (Android 7.0+) |
| Target SDK | 35 (Android 15)   |
| JDK        | 17                |

---

## Installation

### Download APK (Recommended)

Latest Release:
https://github.com/MythicalSHUB/SpatialFlow/releases

> If upgrading from v1.0.0, uninstall once due to signing key change.

---

### Build from Source

```bash
git clone https://github.com/MythicalSHUB/SpatialFlow.git
cd SpatialFlow
```

1. Open in Android Studio (JDK 17)
2. Wait for Gradle sync
3. Build and run on device or emulator

---

## In-App Updater

SpatialFlow includes a built-in updater that:

* Fetches the latest release from GitHub
* Downloads the APK securely
* Installs via the system installer

Path:
Settings → Check for Updates

---

## Screenshots

<!-- Add your screenshots here -->

<!--
<p align="center">
  <img src="screenshots/1.jpg" width="30%">
  <img src="screenshots/2.jpg" width="30%">
  <img src="screenshots/3.jpg" width="30%">
</p>
-->

---

## Versioning

Follows semantic versioning:

```
vMajor.Minor.Patch
```

Example:

```
v1.2.0
```

---

## Contributing

Contributions are welcome.

* Report bugs via Issues
* Suggest features
* Submit pull requests

---

## Developer

Shubham Karande

Focused on clean UI, smooth UX, and modern Android development.

---

## License (MIT)

```
MIT License

Copyright (c) 2026 Shubham Karande

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software...

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```

---

## Support

If you like this project:

* Star the repository
* Fork it
* Share it

---

## Keywords

SpatialFlow, SpatialFlow Android App, SpatialFlow Music Player, SpatialFlow Audio Player, SpatialFlow Offline Music Player,

Shubham Karande, Shubham Karande Developer, Apps by Shubham Karande, Shubham Android Developer,

Android Music Player, Offline Music Player Android, Android Audio Player App, Local Music Player Android, MP3 Player Android App,

Material You Music Player, Material Design 3 Android App, Modern Android UI Music Player, Glassmorphism Android UI,

Lightweight Music Player Android, Fast Android Music Player, Clean UI Music Player Android,

Open Source Android Music Player, Java Android Music Player, Android Media Player Java, FFmpeg Android Audio Player,

Best Offline Music Player Android 2026, Free Android Music Player App, Minimal Music Player Android

``

## File: settings.gradle

``groovy
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven { url 'https://jitpack.io' }  // (Optional here but safe)
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // Required for GitHub libs
        maven { url "https://repo1.maven.org/maven2/" } // Backup repo
    }
}

rootProject.name = "SpatialFlow"
include ':app'
``

