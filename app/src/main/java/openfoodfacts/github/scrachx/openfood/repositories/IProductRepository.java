
package openfoodfacts.github.scrachx.openfood.repositories;

import io.reactivex.Single;
import openfoodfacts.github.scrachx.openfood.models.*;

import java.util.List;

/**
 * This is a repository class working as an Interface.
 * It defines all the functions in Repository component.
 *
 * @author Lobster
 * @since 03.03.18
 */
public interface IProductRepository {


    Single<List<Allergen>> getAllergens();

    Single<List<Category>> getCategories();


    void saveIngredient(Ingredient ingredient);

    void setAllergenEnabled(String allergenTag, Boolean isEnabled);

    Single<LabelName> getLabelByTagAndLanguageCode(String labelTag, String languageCode);

    Single<LabelName> getLabelByTagAndDefaultLanguageCode(String labelTag);

    Single<CountryName> getCountryByTagAndLanguageCode(String labelTag, String languageCode);

    Single<CountryName> getCountryByTagAndDefaultLanguageCode(String labelTag);

    Single<AdditiveName> getAdditiveByTagAndLanguageCode(String additiveTag, String languageCode);

    Single<AdditiveName> getAdditiveByTagAndDefaultLanguageCode(String additiveTag);

    Single<CategoryName> getCategoryByTagAndLanguageCode(String categoryTag, String languageCode);

    Single<CategoryName> getCategoryByTagAndDefaultLanguageCode(String categoryTag);

    Single<List<CategoryName>> getAllCategoriesByLanguageCode(String languageCode);

    Single<List<CategoryName>> getAllCategoriesByDefaultLanguageCode();

    List<Allergen> getEnabledAllergens();

    Single<List<AllergenName>> getAllergensByEnabledAndLanguageCode(Boolean isEnabled, String languageCode);

    Single<List<AllergenName>> getAllergensByLanguageCode(String languageCode);

    Single<AllergenName> getAllergenByTagAndLanguageCode(String allergenTag, String languageCode);

    Single<AllergenName> getAllergenByTagAndDefaultLanguageCode(String allergenTag);

    Single<Question> getSingleProductQuestion(String code, String lang);

    Single<InsightAnnotationResponse> annotateInsight(String insightId, int annotation);

    Single<AnalysisTagConfig> getAnalysisTagConfigByTagAndLanguageCode(String analysisTag, String languageCode);

    void deleteIngredientCascade();
}
