package com.cakcaraka.databinarycompatible.example_ksp;

import com.cakcaraka.databinarycompatible.example_ksp.data.single.MandatoryNonNullableNoOptional;
import com.cakcaraka.databinarycompatible.example_ksp.data.single.MandatoryNonNullableNoOptional2;
import com.cakcaraka.databinarycompatible.example_ksp.data.single.MandatoryNullableNoOptional;
import com.cakcaraka.databinarycompatible.example_ksp.data.single.NoMandatoryOptionalNonNullableFinal;
import com.cakcaraka.databinarycompatible.example_ksp.data.single.NoMandatoryOptionalNullableFinal;
import com.cakcaraka.databinarycompatible.example_ksp.data.single.NoMandatoryOptionalNullableNonFinal;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class TestSingleParamUsage {

    @Test
    public void testNoMandatoryOptionalNullableFinal() {
        NoMandatoryOptionalNullableFinal testEmptyConstructor = new NoMandatoryOptionalNullableFinal();
        NoMandatoryOptionalNullableFinal testEmptyConstructorCopy = testEmptyConstructor.copy(builder -> {
            // no method available
            return null;
        });

        NoMandatoryOptionalNullableFinal testConstructor = new NoMandatoryOptionalNullableFinal(null);
        NoMandatoryOptionalNullableFinal testConstructorCopy = testEmptyConstructor.copy(builder -> {
            // no method available
            return null;
        });
    }

    @Test
    public void testNoMandatoryOptionalNullableNonFinal() {
        NoMandatoryOptionalNullableNonFinal testEmptyConstructor = new NoMandatoryOptionalNullableNonFinal();
        NoMandatoryOptionalNullableNonFinal testEmptyConstructorCopy = testEmptyConstructor.copy(builder -> {
            builder.setTest("test");
            return null;
        });

        NoMandatoryOptionalNullableNonFinal testConstructor = new NoMandatoryOptionalNullableNonFinal(null);
        NoMandatoryOptionalNullableNonFinal testConstructorCopy = testEmptyConstructor.copy(builder -> {
            builder.setTest("test");
            return null;
        });
    }

    @Test
    public void testNoMandatoryOptionalNonNullableFinal() {
        ArrayList<Pair<String, Boolean>> contentAndTestResult = new ArrayList<>();
        contentAndTestResult.add(new Pair<>(null, false));
        contentAndTestResult.add(new Pair<>("null", true));
        for (Pair<String, Boolean> contentAndResult : contentAndTestResult) {
            try {
                NoMandatoryOptionalNonNullableFinal testEmptyConstructor = new NoMandatoryOptionalNonNullableFinal(contentAndResult.getFirst());
                NoMandatoryOptionalNonNullableFinal testEmptyConstructorCopy = testEmptyConstructor.copy(builder -> {
                    // no method available
                    return null;
                });
                Assert.assertTrue("Not Except to success", contentAndResult.getSecond());
            } catch (Exception e) {
                Assert.assertFalse("Not except to fail", contentAndResult.getSecond());
            }
        }
    }

    @Test
    public void testMandatoryNullableNoOptional() {
        MandatoryNullableNoOptional testEmptyConstructor = new MandatoryNullableNoOptional(null);
        MandatoryNullableNoOptional testEmptyConstructorCopy = testEmptyConstructor.copy(builder -> {
            builder.setTest(null);
            builder.setTest("test");
            return null;
        });

        MandatoryNullableNoOptional testConstructor = new MandatoryNullableNoOptional("null");
        MandatoryNullableNoOptional testConstructorCopy = testEmptyConstructor.copy(builder -> {
            builder.setTest(null);
            builder.setTest("test");
            return null;
        });

        MandatoryNonNullableNoOptional2 test = new MandatoryNonNullableNoOptional2("");

        test.copy(builder -> {
            builder.setTest("test");
            return null;
        });
    }

    @Test
    public void testMandatoryNonNullableNoOptional() {
        ArrayList<Pair<String, Boolean>> contentAndTestResult = new ArrayList<>();
        contentAndTestResult.add(new Pair<>(null, false));
        contentAndTestResult.add(new Pair<>("null", true));

        for (Pair<String, Boolean> contentAndResult : contentAndTestResult) {
            try {
                MandatoryNonNullableNoOptional testEmptyConstructor = new MandatoryNonNullableNoOptional(contentAndResult.getFirst());
                Assert.assertTrue("Not Except to success", contentAndResult.getSecond());
            } catch (Exception e) {
                Assert.assertFalse("Not except to fail", contentAndResult.getSecond());
            }
        }

        for (Pair<String, Boolean> contentAndResult : contentAndTestResult) {
            try {
                MandatoryNonNullableNoOptional testEmptyConstructor = new MandatoryNonNullableNoOptional("success");
                MandatoryNonNullableNoOptional testConstructorCopy = testEmptyConstructor.copy(builder -> {
                    builder.setTest(contentAndResult.getFirst());
                    return null;
                });
                Assert.assertTrue("Not Except to success", contentAndResult.getSecond());

                MandatoryNonNullableNoOptional2 test = new MandatoryNonNullableNoOptional2("test", nonMandatoryBuilder -> {
                    nonMandatoryBuilder.setTest2("test");
                    return null;
                });

                test.copy(builder -> {
                            builder.setTest("123");
                            builder.setTest2("test");
                            return null;
                        }
                );
            } catch (Exception e) {
                Assert.assertFalse("Not except to fail", contentAndResult.getSecond());
            }
        }
    }
}
