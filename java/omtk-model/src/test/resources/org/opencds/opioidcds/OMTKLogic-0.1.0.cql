library OMTKLogic version '0.1.0'

using OMTK version '0.1.0'

//define MED_DOSE_FORMS: [MED_DOSE_FORM]

// Given an RxNorm Code:
// NON_SURGICAL_OPIOID_TO_INCLUDE.DRUG_RXCUI
// MED_DRUG.DRUG_RXCUI
// MED_SCDC_FOR_DRUG.DRUG_RXCUI -> SCDC_RXCUI
// MED_SCDC.SCDC_RXCUI (STRENGTH, STRENGTH_VALUE, STRENGTH_UNIT)
// MED_INGREDIENT_FOR_SCDC.SCDC_RXCUI -> INGREDIENT_RXCUI
// MED_INGREDIENT.INGREDIENT_RXCUI
// MED_INGREDIENT_TYPE.INGREDIENT_RXCUI (INGREDIENT_TYPE = 'NonSurgicalOpioid')

/*
 SQL ->
select D.DRUG_RXCUI, D.DRUG_NAME, DF.DOSE_FORM_NAME, SCDCD.SCDC_RXCUI, SCDC.SCDC_NAME, SCDC.STRENGTH, SCDC.STRENGTH_VALUE, SCDC.STRENGTH_UNIT, I.INGREDIENT_RXCUI, I.INGREDIENT_NAME
  from MED_DRUG D
    join NON_SURGICAL_OPIOID_TO_INCLUDE NSO on D.DRUG_RXCUI = NSO.DRUG_RXCUI
    join MED_SCDC_FOR_DRUG SCDCD on D.DRUG_RXCUI = SCDCD.DRUG_RXCUI
    join MED_SCDC SCDC on SCDCD.SCDC_RXCUI = SCDC.SCDC_RXCUI
    join MED_INGREDIENT_FOR_SCDC SCDCI on SCDC.SCDC_RXCUI = SCDCI.SCDC_RXCUI
    join MED_INGREDIENT I on SCDCI.INGREDIENT_RXCUI = I.INGREDIENT_RXCUI
    join MED_INGREDIENT_TYPE IT on I.INGREDIENT_RXCUI = IT.INGREDIENT_RXCUI
	left join MED_DRUG_DOSE_FORM DDF on D.DRUG_RXCUI = DDF.DRUG_RXCUI
	left join MED_DOSE_FORM DF on DDF.DOSE_FORM_RXCUI = DF.DOSE_FORM_RXCUI
	--Most of the drugs have multiple dose form groups...
	--left join MED_DRUG_DOSE_FORM_GROUP DDFG on D.DRUG_RXCUI = DDFG.DRUG_RXCUI
	--left join MED_DOSE_FORM_GROUP DFG on DDFG.DOSE_FORM_GROUP_RXCUI = DFG.DOSE_FORM_GROUP_RXCUI
  where D.DRUG_RXCUI = @RxNormCode // 197696
    and IT.INGREDIENT_TYPE = 'NonSurgicalOpioid'
*/

/*
  CQL, assuming translation to SQL:

  from
    [MED_DRUG: rxNormCode] D,
    [NON_SURGICAL_OPIOID_TO_INCLUDE] NSO,
    [MED_SCDC_FOR_DRUG] SCDCD,
    [MED_SCDC] SCDC,
    [MED_INGREDIENT_FOR_SCDC] SCDCI,
    [MED_INGREDIENT] I,
    [MED_INGREDIENT_TYPE] IT,
    [MED_DRUG_DOSE_FORM] DDF,
    [MED_DOSE_FORM] DF
  where D.DRUG_RXCUI = NSO.DRUG_RXCUI
    and D.DRUG_RXCUI = SCDCD.DRUG_RXCUI
    and SCDCD.SCDC_RXCUI = SCDC.SCDC_RXCUI
    and SCDC.SCDC_RXCUI = SCDCI.SCDC_RXCUI
    and SDCDI.INGREDIENT_RXCUI = I.INGREDIENT_RXCUI
    and I.INGREDIENT_RXCUI = IT.INGREDIENT_RXCUI
    and D.DRUG_RXCUI = DDF.DRUG_RXCUI
    and DDF.DOSE_FORM_RXCUI = DF.DOSE_FORM_RXCUI
    and IT.INGREDIENT_TYPE = 'NonSurgicalOpioid'
*/

/*
An engine with a naive implementation for multi-source queries would perform
pretty horribly here, so rewrite it using "syntactic optimization" :)
*/

/*
  Normalizes the input units to UCUM units
*/
define function ToUCUM(unit String):
  unit

/*
  Returns the conversion factor for the given ingredient
*/
define function GetConversionFactor(ingredientCode Integer):
  1.5

/*
  Returns the non-surgical opioid ingredients and their strengths that 
  make up the drug identified by the given rxNormCode as a list of tuples:

  List<Tuple {
    rxNormCode Integer,
    ingredientCode Integer,
    ingredientName String,
    strength Quantity
  }>
*/
define function GetIngredients(rxNormCode Integer):
  (
    [MED_SCDC_FOR_DRUG: DRUG_RXCUI in rxNormCode] SD
      where exists ([NON_SURGICAL_OPIOID_TO_INCLUDE: DRUG_RXCUI in SD.DRUG_RXCUI])
      return {
        rxNormCode: rxNormCode,
        component: SingletonFrom([MED_SCDC: SCDC_RXCUI in SD.SCDC_RXCUI]),
        ingredientCode: SingletonFrom([MED_INGREDIENT_FOR_SCDC: SCDC_RXCUI in SD.SCDC_RXCUI]).INGREDIENT_RXCUI
      }
  ) C
    let ingredient: SingletonFrom([MED_INGREDIENT: INGREDIENT_RXCUI in C.ingredientCode])
    where exists (
      [MED_INGREDIENT_TYPE: INGREDIENT_RXCUI in C.ingredientCode] IT 
        where IT.INGREDIENT_TYPE = 'NonSurgicalOpioid'
    )
    return {
      rxNormCode: rxNormCode,
      ingredientCode: C.ingredientCode,
      ingredientName: ingredient.INGREDIENT_NAME,
      strength: Quantity {
        value: ToDecimal(C.component.STRENGTH),
        unit: ToUCUM(C.component.STRENGTH_UNIT)
      }
    }
    
/*
  Calculates MMEs for the given input prescription information and returns it
  as a list of tuples:

  List<Tuple { 
    rxNormCode Integer, 
    quantity Integer, 
    daysSupply Integer, 
    ingredientCode Integer, 
    ingredientName String, 
    strength Quantity,
    conversionFactor Decimal,
    mme Quantity
  }>
*/
define function CalculateMMEs(medications List<Tuple { rxNormCode Integer, quantity Integer, daysSupply Integer }>):
  Flatten(
    medications M
      let Ingredients: GetIngredients(M.rxNormCode)
      return
        Ingredients I
          let factor: GetConversionFactor(I.ingredientCode)
          return {
            rxNormCode: M.rxNormCode,
            quantity: M.quantity,
            daysSupply: M.daysSupply,
            ingredientCode: I.ingredientCode,
            ingredientName: I.ingredientName,
            strength: I.strength,
            conversionFactor: factor,
            mme: Quantity { 
              value: I.strength.value * M.quantity / M.daysSupply * factor, 
              unit: I.strength.unit + '/d'
            }
          }
  )
  
define TestCalculateMMEs:
  CalculateMMEs({ { rxNormCode: 197696, quantity: 30, daysSupply: 30 } })
  