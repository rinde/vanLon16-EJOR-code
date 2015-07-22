# LOAD DATA
mydata=read.csv("results/Central-Solver-CheapInsert.csv",header=T)
attach(mydata)

# SIMPLE LINEAR REGRESSION
message("########## SIMPLE LINEAR REGRESSION ##########")
fit.dyn=lm(cost~dynamism,data=mydata)
AIC(fit.dyn) # 39576.96
summary(fit.dyn) # Significant (p < 10^-15)
summary(fit.dyn)$adj.r.squared # 3.1% of variance explained

fit.urg=lm(cost~urgency_mean,data=mydata)
AIC(fit.urg) # 38402.87
summary(fit.urg) # Significant (p < 10^-15)
summary(fit.urg)$adj.r.squared # 43.2% of variance explained

########################################################### 
# MULTIPLE LINEAR REGRESSION (WITHOUT INTERACTIONS)
########################################################### 
message("########## MULTIPLE LINEAR REGRESSION (WITHOUT INTERACTIONS) ##########")
fit1=lm(cost~dynamism+urgency_mean,data=mydata)
summary(fit1) 
anova(fit1) # anova table with overall F test, both factors significant (p < 10^-15)
summary(fit1)$r.squared # 46.4%
summary(fit1)$adj.r.squared # 46.4% of variance explained
AIC(fit1) # AIC (smaller is better) 38277.95
# Compare the above with individual factors
summary(fit.dyn)$adj.r.squared # 3.1% of variance explained
summary(fit.urg)$adj.r.squared # 43.2% of variance explained
AIC(fit.dyn) # 39576.96
AIC(fit.urg) # 38402.87


########################################################### 
# POLYNOMIAL REGRESSION
########################################################### 
message("########## POLYNOMIAL REGRESSION ##########")
fit2=lm(cost~I(urgency_mean^2)+I(urgency_mean)+dynamism,data=mydata) # using raw (untransformed) polynomial terms
fit2=lm(cost~poly(urgency_mean,2,raw=T)+dynamism,data=mydata) # alternative syntax, using raw (untransformed) polynomial terms
fit2rescent=lm(cost~poly(urgency_mean,2)+dynamism,data=mydata) # using residual-normalized polynomial terms
summary(fit2) # Everything significant (p < 10^-15)
summary(fit2)$adj.r.squared # 55.47% of variance explained
summary(fit2rescent) 
summary(fit2rescent)$adj.r.squared # 55.47% of variance explained
library(lmtest) 
lrtest(fit1,fit2) # quadratic model is significantly better than linear model

fit3=lm(cost~I(urgency_mean^2)+dynamism,data=mydata)
lrtest(fit2,fit3) # model with quadratic and linear height term significantly better than model with quadratic height term only
summary(fit3) # Everything significant (p < 10^-15)
summary(fit3)$adj.r.squared # 33.5% of variance explained

fit4=lm(cost~poly(urgency_mean,4,raw=T)+poly(dynamism,2,raw=T),data=mydata) # using raw (untransformed) polynomial terms
summary(fit4) # Everything significant (look at individual p-values, fourth power is the barely significant)
summary(fit4)$adj.r.squared # 59.2% of variance explained, slightly better

AIC(fit4,fit3) # 37680.53 VS 38750.57
lrtest(fit3,fit4) # ^4  model is significantly better than quadratic model

# I want to try if we really need ^2 in dynamism
fit4b=lm(cost~poly(urgency_mean,4,raw=T)+dynamism,data=mydata) # using raw (untransformed) polynomial terms
AIC(fit4,fit4b) # 37680.53 VS 37735.09
lrtest(fit4,fit4b)
# It seems so

# fit4 seems the  best model so far (urgency contributes as ^4, dynamism as ^2)

# REGRESSIONS WITH INTERACTIONS
message("########## REGRESSIONS WITH INTERACTIONS ##########")

fit5=lm(cost~dynamism*urgency_mean,data=mydata) # full factorial model with interaction effect without poly
AIC(fit1) # 38277.95
AIC(fit5) # 38224.33, lower, i.e. better with interaction effect
summary(fit1)$adj.r.squared # 46.3% of variance explained
summary(fit5)$adj.r.squared # 47.7% of variance explained
summary(fit5) # Significant interaction (p < 10^-13)
lrtest(fit1,fit5) # model with interaction effect is significantly better (to me only slightly)


fit6=lm(cost~poly(urgency_mean,4,raw=T)*poly(dynamism,2,raw=T),data=mydata) # full factorial model with polynomials!
AIC(fit4) # 37680.53
AIC(fit6) # 37591.38, lower, i.e. better with interaction effect
summary(fit4)$adj.r.squared # 59.1% of variance explained
summary(fit6)$adj.r.squared # 60.9% of variance explained :-)
summary(fit6) # Here no interactions with individual terms! Only urgency up to ^2 and dynamism ^2 are significant
anova(fit6) # Overall effect of interactions is significant (p < 10^-15)
lrtest(fit4,fit6) # model with interaction effect is significantly better (to me only slightly)

###########################################################
# GENERAL LINEAR MODEL
###########################################################
message("########## GENERAL LINEAR MODEL ##########")
fit.full=lm(cost~poly(dynamism,6)*poly(urgency_mean,6),data=mydata)
AIC(fit.full) # 37622.32

fit.manual=lm(cost~I(dynamism^4)*I(dynamism^3)*I(dynamism^2)*dynamism*I(urgency_mean^4)*I(urgency_mean^3)*I(urgency_mean^2)*urgency_mean,data=mydata)

fit7=lm(cost~poly(urgency_mean,3,raw=T)*poly(dynamism,2,raw=T),data=mydata) # full factorial model with polynomials!

summary(fit7) # Here no interactions with individual terms!
anova(fit7) # Overall effect of interactions is significant (different p-values)

message("R2")
summary(fit7)$adj.r.squared # 60.9% of variance explained 
message("AIC")
AIC(fit7) # 37590.89
