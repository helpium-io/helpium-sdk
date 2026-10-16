#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(HelpiumReactNative, RCTEventEmitter)

RCT_EXTERN_METHOD(configure:(NSString *)widgetKey baseUrl:(NSString *)baseUrl)
RCT_EXTERN_METHOD(setAppearance:(NSDictionary *)appearance)
RCT_EXTERN_METHOD(identify:(NSString *)userJwt email:(NSString *)email name:(NSString *)name)
RCT_EXTERN_METHOD(identifyByEmail:(NSString *)email name:(NSString *)name)
RCT_EXTERN_METHOD(logout)
RCT_EXTERN_METHOD(setLocale:(NSString *)locale)
RCT_EXTERN_METHOD(present)
RCT_EXTERN_METHOD(setPushToken:(NSString *)token)
RCT_EXTERN_METHOD(handlePush:(NSDictionary *)data resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(shouldShowNotification:(NSDictionary *)data resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getUnreadCount:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
