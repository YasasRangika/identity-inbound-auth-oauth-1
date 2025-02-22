/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.identity.oidc.session.servlet;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.Whitebox;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.core.util.KeyStoreManager;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.CommonAuthenticationHandler;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.ServiceURL;
import org.wso2.carbon.identity.core.ServiceURLBuilder;
import org.wso2.carbon.identity.core.URLBuilderException;
import org.wso2.carbon.identity.core.util.IdentityConfigParser;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth.dao.OAuthAppDO;
import org.wso2.carbon.identity.oauth.tokenprocessor.TokenPersistenceProcessor;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.oidc.session.OIDCSessionConstants;
import org.wso2.carbon.identity.oidc.session.OIDCSessionManager;
import org.wso2.carbon.identity.oidc.session.cache.OIDCSessionDataCache;
import org.wso2.carbon.identity.oidc.session.cache.OIDCSessionDataCacheEntry;
import org.wso2.carbon.identity.oidc.session.cache.OIDCSessionDataCacheKey;
import org.wso2.carbon.identity.oidc.session.internal.OIDCSessionManagementComponentServiceHolder;
import org.wso2.carbon.identity.oidc.session.util.OIDCSessionManagementUtil;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@PrepareForTest({OIDCSessionManagementUtil.class, OIDCSessionManager.class, FrameworkUtils.class,
        IdentityConfigParser.class, OAuthServerConfiguration.class, IdentityTenantUtil.class, KeyStoreManager.class,
        CarbonCoreDataHolder.class, IdentityDatabaseUtil.class, OAuth2Util.class,
        OIDCSessionManagementComponentServiceHolder.class, ServiceURLBuilder.class, OIDCSessionDataCache.class})
/*
  Unit test coverage for OIDCLogoutServlet class.
 */
public class OIDCLogoutServletTest extends TestOIDCSessionBase {

    @Mock
    OIDCSessionManager oidcSessionManager;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    CommonAuthenticationHandler commonAuthenticationHandler;

    @Mock
    HttpSession httpSession;

    @Mock
    IdentityConfigParser identityConfigParser;

    @Mock
    OAuthServerConfiguration oAuthServerConfiguration;

    @Mock
    KeyStoreManager keyStoreManager;

    @Mock
    TokenPersistenceProcessor tokenPersistenceProcessor;

    @Mock
    OAuthAppDO oAuthAppDO;

    @Mock
    private ApplicationManagementService mockedApplicationManagementService;

    @Mock
    KeyStore keyStore;

    @Mock
    OIDCSessionDataCache oidcSessionDataCache;

    @Mock
    OIDCSessionDataCacheEntry opbsCacheEntry, sessionIdCacheEntry;

    private static final String CLIENT_ID_VALUE = "3T9l2uUf8AzNOfmGS9lPEIsdrR8a";
    private static final String CLIENT_ID_WITH_REGEX_CALLBACK = "cG1H52zfnkFEh3ULT0yTi14bZRUa";
    private static final String CLIENT_ID_FOR_REALM_TEST = "5GxhmSL89OVpWef4wzioRs1aDYIa";
    private static final String APP_NAME = "myApp";
    private static final String SECRET = "87n9a540f544777860e44e75f605d435";
    private static final String USERNAME = "user1";
    private static final String CALLBACK_URL = "http://localhost:8080/playground2/oauth2client";
    private static final String OPBROWSER_STATE = "090907ce-eab0-40d2-a46d-acd4bb33f0d0";
    private static final int TENANT_ID = -1234;
    private static final String SUPER_TENANT_DOMAIN_NAME = "carbon.super";
    private static final String INVALID_CALLBACK_URL = "http://localhost:8080/playground2/auth";
    private static final String REGEX_CALLBACK_URL = "regexp=http://localhost:8080/playground2/oauth2client";

    private OIDCLogoutServlet logoutServlet;

    @BeforeTest
    public void setUp() throws Exception {

        logoutServlet = new OIDCLogoutServlet();

        initiateInMemoryH2();
        createOAuthApp(CLIENT_ID_VALUE, SECRET, USERNAME, APP_NAME, "ACTIVE", CALLBACK_URL);
        createOAuthApp(CLIENT_ID_WITH_REGEX_CALLBACK, SECRET, USERNAME, APP_NAME, "ACTIVE",
                REGEX_CALLBACK_URL);
        createOAuthApp(CLIENT_ID_FOR_REALM_TEST, SECRET, USERNAME, APP_NAME, "ACTIVE", CALLBACK_URL);

    }

    @DataProvider(name = "provideDataForTestDoGet")
    public Object[][] provideDataForTestDoGet() {

        Cookie opbsCookie = new Cookie("opbs", OPBROWSER_STATE);

        String idTokenHint =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJr" +
                        "aWQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEi" +
                        "LCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6WyIzVDlsMnVVZjhBek5PZm1HUzlsUEV" +
                        "Jc2RyUjhhIl0sImF6cCI6IjNUOWwydVVmOEF6Tk9mbUdTOWxQRUlzZHJSOGEiLCJhdXRoX3RpbWUiOjE" +
                        "1MDcwMDk0MDQsImlzcyI6Imh0dHBzOlwvXC9sb2NhbGhvc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsImV" +
                        "4cCI6MTUwNzAxMzAwNSwibm9uY2UiOiJDcXNVOXdabFFJWUdVQjg2IiwiaWF0IjoxNTA3MDA5NDA1fQ." +
                        "ivgnkuW-EFT7m55Mr1pyit1yALwVxrHjVqmgSley1lUhZNAlJMxefs6kjSbGStQg-mqEv0VQ7NJkZu0w" +
                        "1kYYD_76-KkjI1skP1zEqSXMhTyE8UtQ-CpR1w8bnTU7D50v-537z8vTf7PnTTA-wxpTuoYmv4ya2z0R" +
                        "v-gFTM4KPdxsc7j6yFuQcfWg5SyP9lYpJdt-s-Ow9FY1rlUVvNbtF1u2Fruc1kj9jkjSbvFgSONRhizR" +
                        "H6P_25v0LpgNZrOpiLZF92CtkCBbAGQChWACN6RWDpy5Fj2JuQMNcCvkxlvOVcx-7biH16qVnY9UFs4D" +
                        "xZo2cGzyWbXuH8sDTkzQBg";

        String invalidIdToken =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJr" +
                        "aWQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEi" +
                        "LCJhbGciOiJSUzI1NiJ9.ivgnkuW-EFT7m55Mr1pyit1yALwVxrHjVqmgSley1lUhZNAlJMxefs6kjSbGStQg" +
                        "-mqEv0VQ7NJkZu0w1kYYD_76-KkjI1skP1zEqSXMhTyE8UtQ-CpR1w8bnTU7D50v-537z8vTf7PnTTA-wxpTu" +
                        "oYmv4ya2z0Rv-gFTM4KPdxsc7j6yFuQcfWg5SyP9lYpJdt-s-Ow9FY1rlUVvNbtF1u2Fruc1kj9jkjSbvFgSO" +
                        "NRhizRH6P_25v0LpgNZrOpiLZF92CtkCBbAGQChWACN6RWDpy5Fj2JuQMNcCvkxlvOVcx-7biH16qVnY9UFs4" +
                        "DxZo2cGzyWbXuH8sDTkzQBg";

        String[] redirectUrl = {
                "?oauthErrorCode=access_denied&oauthErrorMsg=opbs+cookie+not+received.+Missing+session+state.",
                "?oauthErrorCode=access_denied&oauthErrorMsg=No+valid+session+found+for+the+received+session+state.",
                "?oauthErrorCode=server_error&oauthErrorMsg=User+logout+failed",
                "?oauthErrorCode=access_denied&oauthErrorMsg=End+User+denied+the+logout+request",
                "https://localhost:8080/playground/oauth2client",
                "https://localhost:9443/authenticationendpoint/oauth2_logout_consent.do",
                "?oauthErrorCode=access_denied&oauthErrorMsg=ID+token+signature+validation+failed.",
                "?oauthErrorCode=access_denied&oauthErrorMsg=Post+logout+URI+does+not+match+with+registered+callback" +
                        "+URI.",
                "?oauthErrorCode=access_denied&oauthErrorMsg=Error+occurred+while+getting+application+information.+C" +
                        "lient+id+not+found",
                "/authenticationendpoint/retry.do"
        };

        String idTokenNotAddedToDB =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJraWQiOiJOVEF" +
                        "4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJhbGciOiJSUzI1NiJ9.e" +
                        "yJzdWIiOiJhZG1pbiIsImF1ZCI6WyJ1NUZJZkc1eHpMdkJHaWFtb0FZenpjcXBCcWdhIl0sImF6cCI6InU1RklmRzV4" +
                        "ekx2QkdpYW1vQVl6emNxcEJxZ2EiLCJhdXRoX3RpbWUiOjE1MDY1NzYwODAsImlzcyI6Imh0dHBzOlwvXC9sb2NhbGh" +
                        "vc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsImV4cCI6MTUwNjU3OTY4NCwibm9uY2UiOiIwZWQ4ZjFiMy1lODNmLTQ2Yz" +
                        "AtOGQ1Mi1mMGQyZTc5MjVmOTgiLCJpYXQiOjE1MDY1NzYwODQsInNpZCI6Ijg3MDZmNWRhLTU0ZmMtNGZiMC1iNGUxL" +
                        "TY5MDZmYTRiMDRjMiJ9.HopPYFs4lInXvGztNEkJKh8Kdy52eCGbzYy6PiVuM_BlCcGff3SHOoZxDH7JbIkPpKBe0cn" +
                        "YQWBxfHuGTUWhvnu629ek6v2YLkaHlb_Lm04xLD9FNxuZUNQFw83pQtDVpoX5r1V-F0DdUc7gA1RKN3xMVYgRyfslRD" +
                        "veGYplxVVNQ1LU3lrZhgaTfcMEsC6rdbd1HjdzG71EPS4674HCSAUelOisNKGa2NgORpldDQsj376QD0G9Mhc8WtWog" +
                        "uftrCCGjBy1kKT4VqFLOqlA-8wUhOj_rZT9SUIBQRDPu0RZobvsskqYo40GEZrUoabrhbwv_QpDTf6-7-nrEjT7WA";

        String idTokenWithRegexCallBack =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJraWQiOiJOVEF" +
                        "4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJhbGciOiJSUzI1NiJ9.e" +
                        "yJzdWIiOiJhZG1pbiIsImF1ZCI6WyJjRzFINTJ6Zm5rRkVoM1VMVDB5VGkxNGJaUlVhIl0sImF6cCI6ImNHMUg1Mnpm" +
                        "bmtGRWgzVUxUMHlUaTE0YlpSVWEiLCJhdXRoX3RpbWUiOjE1MDg0MDcyOTYsImlzcyI6Imh0dHBzOlwvXC9sb2NhbGh" +
                        "vc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsImV4cCI6MTUwODQxMDg5OCwibm9uY2UiOiJDcXNVOXdabFFJWUdVQjg2Ii" +
                        "wiaWF0IjoxNTA4NDA3Mjk4LCJzaWQiOiI3YjI1YzJjOC01YjVlLTQ0YzAtYWVjZS02MDE4ZDgyZTY4MDIifQ.DS9bTh" +
                        "wHV3Ecp_ziYw52B_zpza6sxMqLaVTvH5Qrxxbd9l2iPo56HuSzmT_ul0nzYYHcaQGbuO1LLe6kcSk7wwbbCG7vacjyB" +
                        "nJ4nT8SHGOtTOOjt1srQuNiZlgibi2LbQU0RUFaNq1_3e0PtAQyWOvqugYFbdZc-SgrJSGHet7RxMHTcQxp785hnz8J" +
                        "-lUv5jCrMAuCOJprLzL9EEvX8tHYpmZfyj3UWR8YskLnDmVDnNhqDGtbuZ0Ebn3ppKSsJwsm0ITitQ4uXfYdgEx_EH4" +
                        "gniRThFD2X9rzfP-SXW0eaYHcrRO0zgZr6CIZQNmLQdgc7p5K_AAbPiycod82tg";

        String idTokenHintWithRealm =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJraWQiOiJOVEF" +
                        "4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJhbGciOiJSUzI1NiJ9.e" +
                        "yJhdF9oYXNoIjoiazBvdFlvRV84b21WTnd3ZEJCYWJsdyIsImF1ZCI6IjVHeGhtU0w4OU9WcFdlZjR3emlvUnMxYURZ" +
                        "SWEiLCJjX2hhc2giOiI2Y25ZZ25ZNFBVemNRTHNOSldsX1lBIiwic3ViIjoiYWRtaW4iLCJuYmYiOjE1NTQ0Nzc0MTM" +
                        "sImF6cCI6IjVHeGhtU0w4OU9WcFdlZjR3emlvUnMxYURZSWEiLCJhbXIiOlsiQmFzaWNBdXRoZW50aWNhdG9yIl0sIm" +
                        "lzcyI6Imh0dHBzOlwvXC9sb2NhbGhvc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsInJlYWxtIjp7InVzZXJzdG9yZSI6I" +
                        "lBSSU1BUlkiLCJ0ZW5hbnQiOiJjYXJib24uc3VwZXIifSwiZXhwIjoxNTU0NDgxMDEzLCJpYXQiOjE1NTQ0Nzc0MTMs" +
                        "InNpZCI6ImJjM2IzOTRjLTRjOWQtNGRlOS1iN2MzLTI0YWIwOGNiMmQzZiJ9.KTrYVZ8QrcQFKCL7TIvSZsvLl3VEKx" +
                        "GRXiREg04ej5AEAteSNZZaC6druoymc9z9-9PQMRFknNIh5EUpdT6Z2MuiRJC5_jy2ufFQflUe6ppi5fpvxAGHDK794" +
                        "Rta2jktK1FOdj10Seg0wysMiJ0MqXv52g847wHXnOCHX-LpfFO-paT3R-M8hrcEUiIo4NqW_0tEuY5A2TwBNKnKsKRI" +
                        "NgwwgYcMyX--XZEZVzq-Op41izLehua7Yh88skbRns-v2ViNiVhocgWWc8KjzIip5zeLFuea4Uo2ncMdGw9pUybFa7t" +
                        "RquP67RTvimdKmFv9YzhkdA2RpJFw0k5Ly7BZCA";

        return new Object[][]{
                // opbs cookie is null.
                {null, true, redirectUrl[0], "cookie", "", null, false, "", false, "", null},
                // opbs cookie is existing and there is no any existing sessions.
                {opbsCookie, false, redirectUrl[1], "valid", "", null, false, "", false, "", null},
                // opbs cookie and a previous session are existing and userConsent="Approve".
                {opbsCookie, true, redirectUrl[2], "failed", "approve", null, false, "", false, "", null},
                // opbs cookie and previous session are existing, but the userConsent!="Approve".
                {opbsCookie, true, redirectUrl[3], "denied", "no", null, false, "", false, "", null},
                // opbs cookie and previous session are existing, but user consent is empty and sessionDataKey is
                // empty.
                {opbsCookie, true, redirectUrl[4], "oauth2client", " ", null, true, "", false, "", null},
                // opbs cookie and previous session are existing, user consent is empty and there is a value for
                // sessionDataKey and skipUserConsent=false.
                {opbsCookie, true, redirectUrl[2], "failed", " ", "090907ce-eab0-40d2-a46d", false, "", false, "",
                        null},
                // opbs cookie and previous session are existing, user consent is empty, there is a value for
                // sessionDataKey, skipUserConsent=true and an invalid idTokenHint.
                {opbsCookie, true, redirectUrl[2], "failed", " ", "090907ce-eab0-40d2-a46d", true,
                        "7893-090907ce-eab0-40d2", false, "", null},
                // opbs cookie and previous session are existing, user consent is empty,sessionDataKey = null,
                // skipUserConsent=true and an invalid idTokenHint.
                {opbsCookie, true, redirectUrl[2], "failed", " ", null, true,
                        "7893-090907ce-eab0-40d2", false, "", null},
                // opbs cookie and previous session are existing, user consent is empty,sessionDataKey = null,
                // skipUserConsent=false and a valid idTokenHint.
                {opbsCookie, true, redirectUrl[5], "oauth2_logout_consent.do", " ", null, false,
                        idTokenHint, false, "", null},
                // opbs cookie and previous session are existing, user consent is empty,sessionDataKey = null,
                // skipUserConsent=true and a valid idTokenHint.
                {opbsCookie, true, redirectUrl[5], "", " ", null, true,
                        idTokenHint, false, "", null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=true, a valid idTokenHint, and an invalid postLogoutUri.
                {opbsCookie, true, redirectUrl[5], "oauth2_logout_consent.do", " ", null, true,
                        idTokenHint, false, INVALID_CALLBACK_URL, null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=true, a valid idTokenHint, and valid postLogoutUri.
                {opbsCookie, true, redirectUrl[5], "oauth2_logout_consent.do", " ", null, true,
                        idTokenHint, false, CALLBACK_URL, null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=true, a valid idTokenHint, isJWTSignedWithSPKey= true.
                {opbsCookie, true, redirectUrl[6], "signature", " ", null, true,
                        idTokenHint, true, "", null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=false,idTokenHint=null, isJWTSignedWithSPKey= true.
                {opbsCookie, true, redirectUrl[4], "oauth2client", " ", null, false,
                        null, true, "", null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=false,a valid idTokenHint, isJWTSignedWithSPKey=false, postLogoutUri is invalid.
                {opbsCookie, true, redirectUrl[7], "Post", " ", null, false,
                        idTokenHint, false, INVALID_CALLBACK_URL, null},
                // Idtoken does not have three parts. So throws parse exception.
                {opbsCookie, true, redirectUrl[7], "Post", " ", null, false,
                        invalidIdToken, false, INVALID_CALLBACK_URL, null},
                // Thorws IdentityOAuth2Exception since the id token is not added to DB
                {opbsCookie, true, redirectUrl[8], "application", " ", null, false,
                        idTokenNotAddedToDB, false, INVALID_CALLBACK_URL, null},
                // AuthenticatorFlowStatus = SUCCESS_COMPLETED
                {opbsCookie, true, redirectUrl[5], "oauth2_logout_consent.do", " ", null, true,
                        idTokenHint, false, CALLBACK_URL, AuthenticatorFlowStatus.SUCCESS_COMPLETED},
                // AuthenticatorFlowStatus = INCOMPLETE
                {opbsCookie, true, redirectUrl[9], "retry", " ", null, true,
                        idTokenHint, false, CALLBACK_URL, AuthenticatorFlowStatus.INCOMPLETE},
                // CallBackUrl is a regex one.
                {opbsCookie, true, CALLBACK_URL, "oauth2client", "", null, true, idTokenWithRegexCallBack, false,
                        REGEX_CALLBACK_URL, null},
                // opbs cookie and previous sessions are existing, userConsent is empty, sessionDataKey = null,
                // skipUserConsent=true, a valid idTokenHint with tenant domain in realm, and valid postLogoutUri.
                {opbsCookie, true, redirectUrl[5], "oauth2_logout_consent.do", " ", null, true,
                        idTokenHintWithRealm, false, CALLBACK_URL, null},

        };
    }

    @Test(dataProvider = "provideDataForTestDoGet")
    public void testDoGet(Object cookie, boolean sessionExists, String redirectUrl, String expected, String consent,
                          String sessionDataKey, boolean skipUserConsent, String idTokenHint,
                          boolean isJWTSignedWithSPKey, String postLogoutUrl, Object flowStatus) throws Exception {

        TestUtil.startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

        mockStatic(OIDCSessionManagementUtil.class);
        when(OIDCSessionManagementUtil.handleAlreadyLoggedOutSessionsGracefully()).thenReturn(false);
        when(OIDCSessionManagementUtil.getOPBrowserStateCookie(request)).thenReturn((Cookie) cookie);
        when(OIDCSessionManagementUtil.getErrorPageURL(anyString(), anyString())).thenReturn(redirectUrl);

        mockStatic(OIDCSessionManager.class);
        when(OIDCSessionManagementUtil.getSessionManager()).thenReturn(oidcSessionManager);
        when(oidcSessionManager.sessionExists(OPBROWSER_STATE, MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)).
                thenReturn(sessionExists);

        when(request.getParameter("consent")).thenReturn(consent);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(Arrays.asList(new String[]{"cookie"})));
        when(request.getHeader("COOKIE")).thenReturn("opbs");
        when(request.getAttribute(FrameworkConstants.RequestParams.FLOW_STATUS)).thenReturn(flowStatus);

        doThrow(new ServletException()).when(commonAuthenticationHandler).doPost(request, response);

        when(request.getSession()).thenReturn(httpSession);
        when(httpSession.getMaxInactiveInterval()).thenReturn(2);

        mockStatic(IdentityConfigParser.class);
        when(IdentityConfigParser.getInstance()).thenReturn(identityConfigParser);

        when(request.getParameter("sessionDataKey")).thenReturn(sessionDataKey);

        mockStatic(OAuthServerConfiguration.class);
        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.getOpenIDConnectSkipLogoutConsentConfig()).thenReturn(skipUserConsent);

        when(request.getParameter("id_token_hint")).thenReturn(idTokenHint);

        when(OIDCSessionManagementUtil
                .removeOPBrowserStateCookie(any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenReturn((Cookie) cookie);

        when(OIDCSessionManagementUtil.getOIDCLogoutConsentURL()).thenReturn(redirectUrl);
        when(OIDCSessionManagementUtil.getOIDCLogoutURL()).thenReturn(redirectUrl);

        mockStatic(IdentityTenantUtil.class);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(TENANT_ID);
        when(IdentityTenantUtil.getTenantDomain(TENANT_ID)).thenReturn(SUPER_TENANT_DOMAIN_NAME);

        mockStatic(OAuthServerConfiguration.class);
        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.isJWTSignedWithSPKey()).thenReturn(isJWTSignedWithSPKey);

        mockStatic(KeyStoreManager.class);
        when(KeyStoreManager.getInstance(TENANT_ID)).thenReturn(keyStoreManager);
        when(keyStoreManager.getDefaultPublicKey())
                .thenReturn(TestUtil.getPublicKey(TestUtil.loadKeyStoreFromFileSystem(TestUtil
                        .getFilePath("wso2carbon.jks"), "wso2carbon", "JKS"), "wso2carbon"));

        mockStatic(OIDCSessionManagementComponentServiceHolder.class);
        when(OIDCSessionManagementComponentServiceHolder.getApplicationMgtService())
                .thenReturn(mockedApplicationManagementService);
        when(mockedApplicationManagementService.getServiceProviderNameByClientId(
                anyString(), anyString(), anyString())).thenReturn("SP1");

        mockStatic(OAuthServerConfiguration.class);
        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.getPersistenceProcessor()).thenReturn(tokenPersistenceProcessor);
        when(tokenPersistenceProcessor.getProcessedClientId(anyString()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(request.getParameter("post_logout_redirect_uri")).thenReturn(postLogoutUrl);
        mockStatic(IdentityDatabaseUtil.class);
        when(IdentityDatabaseUtil.getDBConnection()).thenAnswer(invocationOnMock -> dataSource.getConnection());
        mockStatic(OAuth2Util.class);
        when(OAuth2Util.getAppInformationByClientId(anyString())).thenCallRealMethod();
        when(OAuth2Util.getTenantDomainOfOauthApp(anyString())).thenReturn("wso2.com");
        when(OAuth2Util.getTenantDomainOfOauthApp(any(oAuthAppDO.getClass()))).thenReturn("wso2.com");
        when(keyStoreManager.getKeyStore(anyString())).thenReturn(TestUtil.loadKeyStoreFromFileSystem(TestUtil
                .getFilePath("wso2carbon.jks"), "wso2carbon", "JKS"));

        mockServiceURLBuilder(OIDCSessionConstants.OIDCEndpoints.OIDC_LOGOUT_ENDPOINT);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        mockStatic(OIDCSessionDataCache.class);
        when(OIDCSessionDataCache.getInstance()).thenReturn(oidcSessionDataCache);
        OIDCSessionDataCacheKey opbsKey = mock(OIDCSessionDataCacheKey.class);
        OIDCSessionDataCacheKey sessionIdKey = mock(OIDCSessionDataCacheKey.class);
        when(opbsKey.getSessionDataId()).thenReturn(OPBROWSER_STATE);
        when(sessionIdKey.getSessionDataId()).thenReturn(sessionDataKey);
        when(OIDCSessionDataCache.getInstance().getValueFromCache(opbsKey)).thenReturn(opbsCacheEntry);
        when(OIDCSessionDataCache.getInstance().getValueFromCache(sessionIdKey)).thenReturn(sessionIdCacheEntry);
        ConcurrentMap<String, String> paramMap = new ConcurrentHashMap<>();
        paramMap.put(OIDCSessionConstants.OIDC_CACHE_CLIENT_ID_PARAM, CLIENT_ID_VALUE);
        paramMap.put(OIDCSessionConstants.OIDC_CACHE_TENANT_DOMAIN_PARAM, SUPER_TENANT_DOMAIN_NAME);
        when(opbsCacheEntry.getParamMap()).thenReturn(paramMap);
        when(sessionIdCacheEntry.getParamMap()).thenReturn(paramMap);

        logoutServlet.doGet(request, response);
        verify(response).sendRedirect(captor.capture());
        assertTrue(captor.getValue().contains(expected));
    }

    @DataProvider(name = "provideDataForTestHandleMissingSessionStateGracefully")
    public Object[][] provideDataForTestHandleMissingSessionStateGracefully() {

        Cookie opbsCookie = new Cookie("opbs", OPBROWSER_STATE);

        String idTokenHint =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJraWQiOiJOVEF" +
                        "4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJhbGciOiJSUzI1NiJ9.e" +
                        "yJzdWIiOiJhZG1pbiIsImF1ZCI6WyIzVDlsMnVVZjhBek5PZm1HUzlsUEVJc2RyUjhhIl0sImF6cCI6IjNUOWwydVVm" +
                        "OEF6Tk9mbUdTOWxQRUlzZHJSOGEiLCJhdXRoX3RpbWUiOjE1MDcwMDk0MDQsImlzcyI6Imh0dHBzOlwvXC9sb2NhbGh" +
                        "vc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsImV4cCI6MTUwNzAxMzAwNSwibm9uY2UiOiJDcXNVOXdabFFJWUdVQjg2Ii" +
                        "wiaWF0IjoxNTA3MDA5NDA1fQ.ivgnkuW-EFT7m55Mr1pyit1yALwVxrHjVqmgSley1lUhZNAlJMxefs6kjSbGStQg-m" +
                        "qEv0VQ7NJkZu0w1kYYD_76-KkjI1skP1zEqSXMhTyE8UtQ-CpR1w8bnTU7D50v-537z8vTf7PnTTA-wxpTuoYmv4ya2" +
                        "z0Rv-gFTM4KPdxsc7j6yFuQcfWg5SyP9lYpJdt-s-Ow9FY1rlUVvNbtF1u2Fruc1kj9jkjSbvFgSONRhizRH6P_25v0" +
                        "LpgNZrOpiLZF92CtkCBbAGQChWACN6RWDpy5Fj2JuQMNcCvkxlvOVcx-7biH16qVnY9UFs4DxZo2cGzyWbXuH8sDTkz" +
                        "QBg";

        String idTokenHintWithRealm =
                "eyJ4NXQiOiJOVEF4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJraWQiOiJOVEF" +
                        "4Wm1NeE5ETXlaRGczTVRVMVpHTTBNekV6T0RKaFpXSTRORE5sWkRVMU9HRmtOakZpTVEiLCJhbGciOiJSUzI1NiJ9.e" +
                        "yJhdF9oYXNoIjoiazBvdFlvRV84b21WTnd3ZEJCYWJsdyIsImF1ZCI6IjVHeGhtU0w4OU9WcFdlZjR3emlvUnMxYURZ" +
                        "SWEiLCJjX2hhc2giOiI2Y25ZZ25ZNFBVemNRTHNOSldsX1lBIiwic3ViIjoiYWRtaW4iLCJuYmYiOjE1NTQ0Nzc0MTM" +
                        "sImF6cCI6IjVHeGhtU0w4OU9WcFdlZjR3emlvUnMxYURZSWEiLCJhbXIiOlsiQmFzaWNBdXRoZW50aWNhdG9yIl0sIm" +
                        "lzcyI6Imh0dHBzOlwvXC9sb2NhbGhvc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsInJlYWxtIjp7InVzZXJzdG9yZSI6I" +
                        "lBSSU1BUlkiLCJ0ZW5hbnQiOiJjYXJib24uc3VwZXIifSwiZXhwIjoxNTU0NDgxMDEzLCJpYXQiOjE1NTQ0Nzc0MTMs" +
                        "InNpZCI6ImJjM2IzOTRjLTRjOWQtNGRlOS1iN2MzLTI0YWIwOGNiMmQzZiJ9.KTrYVZ8QrcQFKCL7TIvSZsvLl3VEKx" +
                        "GRXiREg04ej5AEAteSNZZaC6druoymc9z9-9PQMRFknNIh5EUpdT6Z2MuiRJC5_jy2ufFQflUe6ppi5fpvxAGHDK794" +
                        "Rta2jktK1FOdj10Seg0wysMiJ0MqXv52g847wHXnOCHX-LpfFO-paT3R-M8hrcEUiIo4NqW_0tEuY5A2TwBNKnKsKRI" +
                        "NgwwgYcMyX--XZEZVzq-Op41izLehua7Yh88skbRns-v2ViNiVhocgWWc8KjzIip5zeLFuea4Uo2ncMdGw9pUybFa7t" +
                        "RquP67RTvimdKmFv9YzhkdA2RpJFw0k5Ly7BZCA";

        String[] postLogoutUrl = {
                "http://localhost:8080/playground2/oauth2client",
                "http://localhost:8080/playground/oauth2client"
        };

        return new Object[][]{
                // No id_token_hint.
                {null, null, null, false, false, "oauth2_logout.do"},
                // No post_logout_redirect_uri.
                {null, idTokenHint, null, false, false, "oauth2_logout.do"},
                // Valid id_token_hint and valid post_logout_redirect_uri.
                {null, idTokenHint, postLogoutUrl[0], false, false, "playground2/oauth2client"},
                // Invalid id_token_hint.
                {null, idTokenHint, postLogoutUrl[0], true, false, "?oauthErrorCode=access_denied"},
                // Invalid post_logout_redirect_uri.
                {null, idTokenHint, postLogoutUrl[1], false, false, "?oauthErrorCode=access_denied"},
                // Invalid session state.
                {opbsCookie, null, null, false, false, "oauth2_logout.do"},
                // Valid id_token_hint with tenant domain in realm and a valid post_logout_redirect_uri.
                {null, idTokenHintWithRealm, postLogoutUrl[0], false, false, "playground2/oauth2client"},
        };
    }

    @Test(dataProvider = "provideDataForTestHandleMissingSessionStateGracefully")
    public void testHandleMissingSessionStateGracefully(
            Object cookie, String idTokenHint, String postLogoutUrl, boolean isJWTSignedWithSPKey,
            boolean sessionExists, String expected) throws Exception {

        String errorPageURL = "?oauthErrorCode=access_denied&oauthErrorMsg=any.";
        String oidcLogoutURL = "https://localhost:9443/authenticationendpoint/oauth2_logout.do";

        TestUtil.startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

        mockStatic(OIDCSessionManagementUtil.class);
        when(OIDCSessionManagementUtil.getOPBrowserStateCookie(request)).thenReturn((Cookie) cookie);
        when(OIDCSessionManagementUtil.handleAlreadyLoggedOutSessionsGracefully()).thenReturn(true);
        when(OIDCSessionManagementUtil.getErrorPageURL(anyString(), anyString())).thenReturn(errorPageURL);
        when(OIDCSessionManagementUtil.getOIDCLogoutURL()).thenReturn(oidcLogoutURL);
        when(OIDCSessionManagementUtil.getSessionManager()).thenReturn(oidcSessionManager);
        when(oidcSessionManager.sessionExists(OPBROWSER_STATE, MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)).
                thenReturn(sessionExists);

        mockStatic(OAuthServerConfiguration.class);
        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.isJWTSignedWithSPKey()).thenReturn(isJWTSignedWithSPKey);
        when(oAuthServerConfiguration.getPersistenceProcessor()).thenReturn(tokenPersistenceProcessor);
        when(tokenPersistenceProcessor.getProcessedClientId(anyString())).thenAnswer(
                invocation -> invocation.getArguments()[0]);

        mockStatic(OAuth2Util.class);
        when(OAuth2Util.getAppInformationByClientId(anyString())).thenCallRealMethod();
        when(OAuth2Util.getTenantDomainOfOauthApp(any(oAuthAppDO.getClass()))).thenReturn("wso2.com");

        mockStatic(IdentityTenantUtil.class);
        when(IdentityTenantUtil.getTenantId(anyString())).thenReturn(TENANT_ID);

        mockStatic(IdentityConfigParser.class);
        when(IdentityConfigParser.getInstance()).thenReturn(identityConfigParser);

        mockStatic(IdentityDatabaseUtil.class);
        when(IdentityDatabaseUtil.getDBConnection()).thenAnswer(invocationOnMock -> dataSource.getConnection());

        mockStatic(KeyStoreManager.class);
        when(KeyStoreManager.getInstance(TENANT_ID)).thenReturn(keyStoreManager);
        when(keyStoreManager.getDefaultPublicKey())
                .thenReturn(TestUtil.getPublicKey(TestUtil.loadKeyStoreFromFileSystem(TestUtil
                        .getFilePath("wso2carbon.jks"), "wso2carbon", "JKS"), "wso2carbon"));
        when(keyStoreManager.getKeyStore(anyString())).thenReturn(TestUtil.loadKeyStoreFromFileSystem(TestUtil
                .getFilePath("wso2carbon.jks"), "wso2carbon", "JKS"));

        when(request.getParameter("id_token_hint")).thenReturn(idTokenHint);
        when(request.getParameter("post_logout_redirect_uri")).thenReturn(postLogoutUrl);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        logoutServlet.doGet(request, response);
        verify(response).sendRedirect(captor.capture());
        assertTrue(captor.getValue().contains(expected));
    }

    @DataProvider(name = "provideDataForStateParamTest")
    public Object[][] provideDataForStateParamTest() {

        String postLogoutUrlWithQueryParam = "http://localhost:8080/playground2/oauth2client?x=y";
        String postLogoutUrlWithoutQueryParam = "http://localhost:8080/playground2/oauth2client";
        String stateParam = "n6556";

        return new Object[][]{
                {postLogoutUrlWithQueryParam, stateParam,
                        "http://localhost:8080/playground2/oauth2client?x=y&state=n6556"},
                {postLogoutUrlWithQueryParam, "", "http://localhost:8080/playground2/oauth2client?x=y"},
                {postLogoutUrlWithoutQueryParam, stateParam, "http://localhost:8080/playground2/oauth2client?state" +
                        "=n6556"},
                {postLogoutUrlWithoutQueryParam, "", "http://localhost:8080/playground2/oauth2client"},
        };
    }

    @Test(dataProvider = "provideDataForStateParamTest")
    public void testStateParam(String postLogoutUrl, String stateParam, String outputRedirectUrl) throws Exception {

        TestUtil.startTenantFlow(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

        Object expected = Whitebox.invokeMethod(logoutServlet, "appendStateQueryParam",
                postLogoutUrl, stateParam);
        assertEquals(expected, outputRedirectUrl);
    }

    @AfterTest
    public void cleanData() throws Exception {

        super.cleanData();
    }

    private void mockServiceURLBuilder(String context) throws URLBuilderException {

        mockStatic(ServiceURLBuilder.class);
        ServiceURLBuilder serviceURLBuilder = mock(ServiceURLBuilder.class);
        when(ServiceURLBuilder.create()).thenReturn(serviceURLBuilder);
        when(serviceURLBuilder.addPath(any())).thenReturn(serviceURLBuilder);

        ServiceURL serviceURL = mock(ServiceURL.class);
        when(serviceURL.getRelativeInternalURL()).thenReturn(context);
        when(serviceURLBuilder.build()).thenReturn(serviceURL);
    }
}
