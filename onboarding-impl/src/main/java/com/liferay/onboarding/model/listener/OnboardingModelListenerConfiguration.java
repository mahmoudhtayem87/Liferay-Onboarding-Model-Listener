package com.liferay.onboarding.model.listener;

import aQute.bnd.annotation.metatype.Meta;

@Meta.OCD(
        id = OnboardingModelListenerConfiguration.PID,
        localization = "content/Language",
        name = "form-user-account-listener-config-name"
)
interface OnboardingModelListenerConfiguration {

    @Meta.AD(
            name = "form-user-account-listener-form-id",
            description = "You can find this information after creating a form under 'Content & Data > Forms' in a site",
            required = false,
            deflt = "0"
    )
    public long formId();

    @Meta.AD(
            name = "email-address-field",
            description = "Form Field ID",
            required = true,
            deflt = "0"
    )
    public String emailAddressField();

    @Meta.AD(
            name = "first-name",
            description = "Form Field ID",
            required = true,
            deflt = "0"
    )
    public String firstName();

    @Meta.AD(
            name = "middle-name",
            description = "Form Field ID",
            required = true,
            deflt = "0"
    )
    public String middleName();

    @Meta.AD(
            name = "last-name",
            description = "Form Field ID",
            required = true,
            deflt = "0"
    )
    public String lastName();

    @Meta.AD(
            name = "role-id",
            description = "Assign approved customer to this role id",
            required = true,
            deflt = "0"
    )
    public long roleID();

    @Meta.AD(
            name = "create-user",
            description = "Create new user once the form is submitted",
            required = true,
            deflt = "false"
    )
    public boolean CreateUser();

    @Meta.AD(
            name = "add-user-to-role",
            description = "Assign role to user, once the form is approved; If Create-User is not selected, the form submittor will be assigned to the role",
            required = true,
            deflt = "false"
    )
    public boolean AddToRole();

    @Meta.AD(
            name = "active",
            description = "Start monitoring the form if active is equal to true",
            required = false,
            deflt = "false"
    )
    public boolean Active();

    public static final String PID = "com.liferay.onboarding.model.listener.OnboardingModelListenerConfiguration";

}
