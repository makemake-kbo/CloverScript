/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.HttpCallManager;
import org.floens.chan.core.site.http.LoginRequest;
import org.floens.chan.core.site.http.LoginResponse;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.AnimationUtils;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.utils.AndroidUtils.getString;

public class PassSettingsController extends Controller implements View.OnClickListener, Site.LoginListener {
    @Inject
    ReplyManager replyManager;

    @Inject
    HttpCallManager httpCallManager;

    private LinearLayout container;
    private CrossfadeView crossfadeView;
    private TextView errors;
    private Button button;
    private TextView bottomDescription;
    private EditText inputToken;
    private EditText inputPin;
    private TextView authenticated;

    private Site site;

    public PassSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getGraph().inject(this);

        // TODO(multi-site) some selector of some sorts
        site = Sites.defaultSite();

        navigationItem.setTitle(R.string.settings_screen_pass);

        view = inflateRes(R.layout.controller_pass);
        container = (LinearLayout) view.findViewById(R.id.container);
        crossfadeView = (CrossfadeView) view.findViewById(R.id.crossfade);
        errors = (TextView) view.findViewById(R.id.errors);
        button = (Button) view.findViewById(R.id.button);
        bottomDescription = (TextView) view.findViewById(R.id.bottom_description);
        inputToken = (EditText) view.findViewById(R.id.input_token);
        inputPin = (EditText) view.findViewById(R.id.input_pin);
        authenticated = (TextView) view.findViewById(R.id.authenticated);

        AnimationUtils.setHeight(errors, false, false);

        final boolean loggedIn = loggedIn();
        button.setText(loggedIn ? R.string.setting_pass_logout : R.string.setting_pass_login);
        button.setOnClickListener(this);

        bottomDescription.setText(Html.fromHtml(getString(R.string.setting_pass_bottom_description)));
        bottomDescription.setMovementMethod(LinkMovementMethod.getInstance());

        LoginRequest loginDetails = site.getLoginDetails();
        inputToken.setText(loginDetails.user);
        inputPin.setText(loginDetails.pass);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        // TODO: remove
        AndroidUtils.waitForLayout(parentController.view.getViewTreeObserver(), view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                crossfadeView.getLayoutParams().height = crossfadeView.getHeight();
                crossfadeView.requestLayout();
                crossfadeView.toggle(!loggedIn, false);
                return false;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == button) {
            if (loggedIn()) {
                deauth();
                crossfadeView.toggle(true, true);
                button.setText(R.string.setting_pass_login);
                hideError();
                ((PassSettingControllerListener) previousSiblingController).onPassEnabledChanged(false);
            } else {
                auth();
            }
        }
    }

    @Override
    public void onLoginComplete(HttpCall httpCall, LoginResponse loginResponse) {
        if (loginResponse.success) {
            authSuccess(loginResponse);
        } else {
            authFail(loginResponse);
        }

        authAfter();
    }

    @Override
    public void onLoginError(HttpCall httpCall) {
        authFail(null);
        authAfter();
    }

    private void authSuccess(LoginResponse response) {
        crossfadeView.toggle(false, true);
        button.setText(R.string.setting_pass_logout);
        authenticated.setText(response.message);
        ((PassSettingControllerListener) previousSiblingController).onPassEnabledChanged(true);
    }

    private void authFail(LoginResponse response) {
        String message = getString(R.string.setting_pass_error);
        if (response != null && response.message != null) {
            message = response.message;
        }

        showError(message);
        button.setText(R.string.setting_pass_login);
    }

    private void authAfter() {
        button.setEnabled(true);
        inputToken.setEnabled(true);
        inputPin.setEnabled(true);
    }

    private void auth() {
        AndroidUtils.hideKeyboard(view);
        inputToken.setEnabled(false);
        inputPin.setEnabled(false);
        button.setEnabled(false);
        button.setText(R.string.setting_pass_logging_in);
        hideError();

        // TODO(multi-site)
        String user = inputToken.getText().toString();
        String pass = inputPin.getText().toString();
        site.login(new LoginRequest(user, pass), this);
    }

    private void deauth() {
        site.logout();
    }

    private void showError(String error) {
        errors.setText(error);
        AnimationUtils.setHeight(errors, true, true, container.getWidth());
    }

    private void hideError() {
        errors.setText(null);
        AnimationUtils.setHeight(errors, false, true, container.getHeight());
    }

    private boolean loggedIn() {
        return site.isLoggedIn();
    }

    public interface PassSettingControllerListener {
        void onPassEnabledChanged(boolean enabled);
    }
}
