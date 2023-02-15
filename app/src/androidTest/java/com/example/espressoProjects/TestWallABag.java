package com.example.espressoProjects;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.renderscript.RenderScript;

import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.PriorityQueue;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.ui.MainActivity;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestWallABag {

    @Rule
    public ActivityScenarioRule<MainActivity> mActivityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void aaatestExample() throws InterruptedException {
        onView(withText("OK")).perform(click());
        Thread.sleep(2000);
        onView(withText("OK")).perform(click());
        Thread.sleep(3000);
    }

    @Test
    public void aathreeBar() throws InterruptedException {
        aaatestExample();
        ViewInteraction threeBar=onView(withContentDescription("Open navigation drawer"));
        threeBar.perform(click());

    }

    @Test
    public void addNewEntry() throws InterruptedException {
        aathreeBar();
        onView(withText("Add new entry")).perform(click());
        onView(withId(R.id.page_url)).perform(typeText("https://www.rd.com/list/uplifting-quotes/"));
    }
}
