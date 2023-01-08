/*
 * Copyright 2012, 2014 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.xpchainj.uri;

import io.xpchainj.core.Address;
import io.xpchainj.core.LegacyAddress;
import io.xpchainj.core.NetworkParameters;
import io.xpchainj.core.SegwitAddress;
import io.xpchainj.params.MainNetParams;
import io.xpchainj.params.TestNet3Params;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static io.xpchainj.core.Coin.CENT;
import static io.xpchainj.core.Coin.parseCoin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XpchainURITest {
    private XpchainURI testObject = null;

    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final String MAINNET_GOOD_ADDRESS = "1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH";
    private static final String MAINNET_GOOD_SEGWIT_ADDRESS = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4";
    private static final String BITCOIN_SCHEME = MAINNET.getUriScheme();

    @Test
    public void testConvertToBitcoinURI() {
        Address goodAddress = LegacyAddress.fromBase58(MAINNET, MAINNET_GOOD_ADDRESS);
        
        // simple example
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello&message=AMessage", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("12.34"), "Hello", "AMessage"));
        
        // example with spaces, ampersand and plus
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello%20World&message=Mess%20%26%20age%20%2B%20hope", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("12.34"), "Hello World", "Mess & age + hope"));

        // no amount, label present, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello&message=glory", XpchainURI.convertToBitcoinURI(goodAddress, null, "Hello", "glory"));
        
        // amount present, no label, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("0.1"), null, "glory"));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("0.1"), "", "glory"));

        // amount present, label present, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("12.34"), "Hello", null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("12.34"), "Hello", ""));
              
        // amount present, no label, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("1000"), null, null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000", XpchainURI.convertToBitcoinURI(goodAddress, parseCoin("1000"), "", ""));
        
        // no amount, label present, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello", XpchainURI.convertToBitcoinURI(goodAddress, null, "Hello", null));
        
        // no amount, no label, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha", XpchainURI.convertToBitcoinURI(goodAddress, null, null, "Agatha"));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha", XpchainURI.convertToBitcoinURI(goodAddress, null, "", "Agatha"));
      
        // no amount, no label, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, XpchainURI.convertToBitcoinURI(goodAddress, null, null, null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, XpchainURI.convertToBitcoinURI(goodAddress, null, "", ""));

        // different scheme
        final NetworkParameters alternativeParameters = new MainNetParams() {
            @Override
            public String getUriScheme() {
                return "test";
            }
        };

        assertEquals("test:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello&message=AMessage",
             XpchainURI.convertToBitcoinURI(LegacyAddress.fromBase58(alternativeParameters, MAINNET_GOOD_ADDRESS), parseCoin("12.34"), "Hello", "AMessage"));
    }

    @Test
    public void testConvertToBitcoinURI_segwit() {
        assertEquals("bitcoin:" + MAINNET_GOOD_SEGWIT_ADDRESS + "?message=segwit%20rules", XpchainURI.convertToBitcoinURI(
                SegwitAddress.fromBech32(MAINNET, MAINNET_GOOD_SEGWIT_ADDRESS), null, null, "segwit rules"));
    }

    @Test
    public void testGood_legacy() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS);
        assertEquals(MAINNET_GOOD_ADDRESS, testObject.getAddress().toString());
        assertNull("Unexpected amount", testObject.getAmount());
        assertNull("Unexpected label", testObject.getLabel());
        assertEquals("Unexpected label", 20, testObject.getAddress().getHash().length);
    }

    @Test
    public void testGood_uppercaseScheme() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME.toUpperCase(Locale.US) + ":" + MAINNET_GOOD_ADDRESS);
        assertEquals(MAINNET_GOOD_ADDRESS, testObject.getAddress().toString());
        assertNull("Unexpected amount", testObject.getAmount());
        assertNull("Unexpected label", testObject.getLabel());
        assertEquals("Unexpected label", 20, testObject.getAddress().getHash().length);
    }

    @Test
    public void testGood_segwit() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_SEGWIT_ADDRESS);
        assertEquals(MAINNET_GOOD_SEGWIT_ADDRESS, testObject.getAddress().toString());
        assertNull("Unexpected amount", testObject.getAmount());
        assertNull("Unexpected label", testObject.getLabel());
    }

    /**
     * Test a broken URI (bad scheme)
     */
    @Test
    public void testBad_Scheme() {
        try {
            testObject = new XpchainURI(MAINNET, "blimpcoin:" + MAINNET_GOOD_ADDRESS);
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
        }
    }

    /**
     * Test a broken URI (bad syntax)
     */
    @Test
    public void testBad_BadSyntax() {
        // Various illegal characters
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + "|" + MAINNET_GOOD_ADDRESS);
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }

        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "\\");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }

        // Separator without field
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }
    }

    /**
     * Test a broken URI (missing address)
     */
    @Test
    public void testBad_Address() {
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME);
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
        }
    }

    /**
     * Test a broken URI (bad address type)
     */
    @Test
    public void testBad_IncorrectAddressType() {
        try {
            testObject = new XpchainURI(TESTNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS);
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("Bad address"));
        }
    }

    /**
     * Handles a simple amount
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Amount() throws XpchainURIParseException {
        // Test the decimal parsing
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210.12345678");
        assertEquals("654321012345678", testObject.getAmount().toString());

        // Test the decimal parsing
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=.12345678");
        assertEquals("12345678", testObject.getAmount().toString());

        // Test the integer parsing
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210");
        assertEquals("654321000000000", testObject.getAmount().toString());
    }

    /**
     * Handles a simple label
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Label() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?label=Hello%20World");
        assertEquals("Hello World", testObject.getLabel());
    }

    /**
     * Handles a simple label with an embedded ampersand and plus
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_LabelWithAmpersandAndPlus() throws XpchainURIParseException {
        String testString = "Hello Earth & Mars + Venus";
        String encodedLabel = XpchainURI.encodeURLString(testString);
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label="
                + encodedLabel);
        assertEquals(testString, testObject.getLabel());
    }

    /**
     * Handles a Russian label (Unicode test)
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_LabelWithRussian() throws XpchainURIParseException {
        // Moscow in Russian in Cyrillic
        String moscowString = "\u041c\u043e\u0441\u043a\u0432\u0430";
        String encodedLabel = XpchainURI.encodeURLString(moscowString);
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label="
                + encodedLabel);
        assertEquals(moscowString, testObject.getLabel());
    }

    /**
     * Handles a simple message
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Message() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?message=Hello%20World");
        assertEquals("Hello World", testObject.getMessage());
    }

    /**
     * Handles various well-formed combinations
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Combinations() throws XpchainURIParseException {
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210&label=Hello%20World&message=Be%20well");
        assertEquals(
                "BitcoinURI['amount'='654321000000000','label'='Hello World','message'='Be well','address'='1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH']",
                testObject.toString());
    }

    /**
     * Handles a badly formatted amount field
     */
    @Test
    public void testBad_Amount() {
        // Missing
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                    + "?amount=");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("amount"));
        }

        // Non-decimal (BIP 21)
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                    + "?amount=12X4");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("amount"));
        }
    }

    @Test
    public void testEmpty_Label() throws XpchainURIParseException {
        assertNull(new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?label=").getLabel());
    }

    @Test
    public void testEmpty_Message() throws XpchainURIParseException {
        assertNull(new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?message=").getMessage());
    }

    /**
     * Handles duplicated fields (sneaky address overwrite attack)
     */
    @Test
    public void testBad_Duplicated() {
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                    + "?address=aardvark");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("address"));
        }
    }

    @Test
    public void testGood_ManyEquals() throws XpchainURIParseException {
        assertEquals("aardvark=zebra", new XpchainURI(MAINNET, BITCOIN_SCHEME + ":"
                + MAINNET_GOOD_ADDRESS + "?label=aardvark=zebra").getLabel());
    }
    
    /**
     * Handles unknown fields (required and not required)
     * 
     * @throws XpchainURIParseException
     *             If something goes wrong
     */
    @Test
    public void testUnknown() throws XpchainURIParseException {
        // Unknown not required field
        testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?aardvark=true");
        assertEquals("BitcoinURI['aardvark'='true','address'='1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH']", testObject.toString());

        assertEquals("true", testObject.getParameterByName("aardvark"));

        // Unknown not required field (isolated)
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                    + "?aardvark");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("no separator"));
        }

        // Unknown and required field
        try {
            testObject = new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                    + "?req-aardvark=true");
            fail("Expecting BitcoinURIParseException");
        } catch (XpchainURIParseException e) {
            assertTrue(e.getMessage().contains("req-aardvark"));
        }
    }

    @Test
    public void brokenURIs() throws XpchainURIParseException {
        // Check we can parse the incorrectly formatted URIs produced by blockchain.info and its iPhone app.
        String str = "bitcoin://1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH?amount=0.01000000";
        XpchainURI uri = new XpchainURI(str);
        assertEquals("1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH", uri.getAddress().toString());
        assertEquals(CENT, uri.getAmount());
    }

    @Test(expected = XpchainURIParseException.class)
    public void testBad_AmountTooPrecise() throws XpchainURIParseException {
        new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=0.123456789");
    }

    @Test(expected = XpchainURIParseException.class)
    public void testBad_NegativeAmount() throws XpchainURIParseException {
        new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=-1");
    }

    @Test(expected = XpchainURIParseException.class)
    public void testBad_TooLargeAmount() throws XpchainURIParseException {
        new XpchainURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=100000000");
    }

    @Test
    public void testPaymentProtocolReq() throws Exception {
        // Non-backwards compatible form ...
        XpchainURI uri = new XpchainURI(TESTNET, "bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin%2Ff.php%3Fh%3Db0f02e7cea67f168e25ec9b9f9d584f9");
        assertEquals("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9", uri.getPaymentRequestUrl());
        assertEquals(Collections.singletonList("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9"),
                uri.getPaymentRequestUrls());
        assertNull(uri.getAddress());
    }

    @Test
    public void testMultiplePaymentProtocolReq() throws Exception {
        XpchainURI uri = new XpchainURI(MAINNET,
                "bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin&r1=bt:112233445566");
        assertEquals(Arrays.asList("bt:112233445566", "https://bitcoincore.org/~gavin"), uri.getPaymentRequestUrls());
        assertEquals("https://bitcoincore.org/~gavin", uri.getPaymentRequestUrl());
    }

    @Test
    public void testNoPaymentProtocolReq() throws Exception {
        XpchainURI uri = new XpchainURI(MAINNET, "bitcoin:" + MAINNET_GOOD_ADDRESS);
        assertNull(uri.getPaymentRequestUrl());
        assertEquals(Collections.emptyList(), uri.getPaymentRequestUrls());
        assertNotNull(uri.getAddress());
    }

    @Test
    public void testUnescapedPaymentProtocolReq() throws Exception {
        XpchainURI uri = new XpchainURI(TESTNET,
                "bitcoin:?r=https://merchant.com/pay.php?h%3D2a8628fc2fbe");
        assertEquals("https://merchant.com/pay.php?h=2a8628fc2fbe", uri.getPaymentRequestUrl());
        assertEquals(Collections.singletonList("https://merchant.com/pay.php?h=2a8628fc2fbe"), uri.getPaymentRequestUrls());
        assertNull(uri.getAddress());
    }
}
