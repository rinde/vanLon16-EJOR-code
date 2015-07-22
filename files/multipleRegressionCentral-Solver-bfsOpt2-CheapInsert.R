list.of.packages <- c("lmtest")
new.packages <- list.of.packages[!(list.of.packages %in% installed.packages()[,"Package"])]
if(length(new.packages)) install.packages(new.packages)
library(lmtest) 

# LOAD DATA
mydata=read.csv("results/Central-Solver-bfsOpt2-CheapInsert.csv",header=T)
attach(mydata)

# SIMPLE LINEAR REGRESSION
message("########## SIMPLE LINEAR REGRESSION ##########")
fit.dyn=lm(cost~dynamism,data=mydata)
AIC(fit.dyn) #39694.78
summary(fit.dyn) # Significant! (p <  10^-6)
summary(fit.dyn)$adj.r.squared # 1.2% of variance explained

fit.urg=lm(cost~urgency_mean,data=mydata)
AIC(fit.urg) # 37679.74
summary(fit.urg) # significant (p < 10^-15)
summary(fit.urg)$adj.r.squared # 60.5% of variance explained


########################################################### 
# MULTIPLE LINEAR REGRESSION (WITHOUT INTERACTIONS)
########################################################### 
message("########## MULTIPLE LINEAR REGRESSION (WITHOUT INTERACTIONS) ##########")
fit1=lm(cost~dynamism+urgency_mean,data=mydata)
summary(fit1)
anova(fit1) # anova table with overall F test, both factors significant (p < 10^-15)
summary(fit1)$r.squared # 61.7%
summary(fit1)$adj.r.squared # 61.7% of variance explained
AIC(fit1,fit.urg) # AIC (smaller is better) 37679.74 VS 37610.28
summary(fit.dyn)$adj.r.squared # 1.2% of variance explained
summary(fit.urg)$adj.r.squared # 60.5% of variance explained


########################################################### 
# POLYNOMIAL REGRESSION
########################################################### 
message("########## POLYNOMIAL REGRESSION ##########")
fit2=lm(cost~I(urgency_mean^2)+I(urgency_mean)+dynamism,data=mydata) # using raw (untransformed) polynomial terms
fit2=lm(cost~poly(urgency_mean,2,raw=T)+dynamism,data=mydata) # alternative syntax, using raw (untransformed) polynomial terms
fit2rescent=lm(cost~poly(urgency_mean,2)+dynamism,data=mydata) # using residual-normalized polynomial terms
summary(fit2) # p < 10^-15
summary(fit2)$adj.r.squared # 90.0% of variance explained
summary(fit2rescent) # p < 10^-15
summary(fit2rescent)$adj.r.squared # 70.2% of variance explained

lrtest(fit1,fit2) # quadratic model is significantly better than linear model

fit3=lm(cost~I(urgency_mean^2)+dynamism,data=mydata)
lrtest(fit2,fit3) # model with quadratic and linear height term significantly better than model with quadratic height term only
summary(fit3) # Everything significant (p < 10^-12)
summary(fit3)$adj.r.squared # 46.06% of variance explained

fit4=lm(cost~poly(urgency_mean,4,raw=T)+poly(dynamism,2,raw=T),data=mydata) # using raw (untransformed) polynomial terms
summary(fit4) # Significant, but look at individual p-values
summary(fit4)$adj.r.squared # 72.2% of variance explained, slightly better
AIC(fit4,fit3) # 36911.88 VS 38364.78

lrtest(fit3,fit4) # ^4  model is significantly better than quadratic model

# I want to try if we really need ^2 in dynamism
fit4b=lm(cost~poly(urgency_mean,4,raw=T)+dynamism,data=mydata) # using raw (untransformed) polynomial terms
AIC(fit4,fit4b)
lrtest(fit4,fit4b)
# It seems so

# fit4 seems the  best model so far (urgency contributes as ^4, dynamism as ^2)

# REGRESSIONS WITH INTERACTIONS
message("########## REGRESSIONS WITH INTERACTIONS ##########")
fit5=lm(cost~dynamism*urgency_mean,data=mydata) # full factorial model with interaction effect without poly
AIC(fit1) # 37610.28
AIC(fit5) # 37565.79, lower, i.e. better with interaction effect
summary(fit1)$adj.r.squared # 61.7% of variance explained
summary(fit5)$adj.r.squared # 62.5% of variance explained
summary(fit5) # Significant interaction p < 10^-11
lrtest(fit1,fit5) # model with interaction effect is significantly better (to me only slightly)
summary(fit5)


fit6=lm(cost~poly(urgency_mean,4,raw=T)*poly(dynamism,2,raw=T),data=mydata) # full factorial model with polynomials!
AIC(fit4) # 36911.88
AIC(fit6) # 36830.17, lower, i.e. better with interaction effect
summary(fit4)$adj.r.squared # 72.2% of variance explained
summary(fit6)$adj.r.squared # 73.3% of variance explained :-)
summary(fit6) # Some significant interactions (not all, look at all p-values)
anova(fit6) # Overall effect of interactions is significant (p < 10^-15)
lrtest(fit4,fit6) # model with interaction effect is significantly better (to me only slightly)
summary(fit6)

###########################################################
# GENERAL LINEAR MODEL 
###########################################################
message("########## GENERAL LINEAR MODEL ##########")
fit.full=lm(cost~poly(dynamism,6)*poly(urgency_mean,6),data=mydata)
AIC(fit.full) # 36858.57

fit7=lm(cost~poly(urgency_mean,3,raw=T)*poly(dynamism,2,raw=T),data=mydata) # full factorial model with polynomials!


summary(fit7) # Here no interactions with individual terms!
anova(fit7) # Overall effect of interactions is significant (p-values < 10^-15)
message("R2")
summary(fit7)$adj.r.squared # 73.2% of variance explained 
message("AIC")
AIC(fit7) # 36833.01
